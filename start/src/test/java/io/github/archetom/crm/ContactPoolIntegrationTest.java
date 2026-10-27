package io.github.archetom.crm;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContactCreateRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolAssignRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolCreateRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolContactBatchRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolRuleRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolTransferRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactVersionRequest;
import io.github.archetom.crm.api.dto.request.CustomerCreateRequest;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.RoleAssignmentCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldAccessRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldPolicyReplaceRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantRequest;
import io.github.archetom.crm.api.dto.request.SavedViewCreateRequest;
import io.github.archetom.crm.api.dto.response.ContactPoolResponse;
import io.github.archetom.crm.api.dto.response.ContactResponse;
import io.github.archetom.crm.api.dto.response.CustomerResponse;
import io.github.archetom.crm.api.dto.response.SavedViewResponse;
import io.github.archetom.crm.api.dto.response.TenantRoleResponse;
import io.github.archetom.crm.application.port.out.ContactPoolCatalogQuery;
import io.github.archetom.crm.application.service.ContactPoolAutomationService;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RequiresDatabase
class ContactPoolIntegrationTest extends BaseIntegrationTest {

    private static final long OWNER_ACTOR_ID = 888888L;
    private static final long TENANT_ID = 999999L;

    @Autowired
    private ContactPoolAutomationService automationService;
    @Autowired
    private ContactPoolCatalogQuery poolCatalogQuery;

    @Test
    void catalogIncludesTenantOwnerPoolWithoutExplicitEligibility() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pool Catalog Owner");
        long poolAdministrator = 777702L;
        seedActiveMember(poolAdministrator, "Pool Administrator");
        ContactPoolCreateRequest request = new ContactPoolCreateRequest()
                .setName("Owner-only catalog pool")
                .setAdministratorActorIds(List.of(poolAdministrator))
                .setEligibleActorIds(List.of(poolAdministrator))
                .setEligibleDepartmentIds(List.of()).setDailyClaimLimit(0)
                .setPreviousOwnerCooldownDays(0).setAutomaticReleaseEnabled(false)
                .setRules(List.of());
        ContactPoolResponse pool = fromJson(performPost("/api/v1/contact-pools", request)
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactPoolResponse.class);

        performGet("/api/v1/contact-pools/catalog").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(pool.getId()))
                .andExpect(jsonPath("$[0].administrator").value(true));
        List<ContactPoolCatalogQuery.Entry> administratorCatalog = poolCatalogQuery.findAccessible(
                new TenantId(TENANT_ID), new MemberId(poolAdministrator), null, false);
        assertEquals(List.of(pool.getId()), administratorCatalog.stream()
                .map(entry -> entry.id().getValue()).toList());
        assertTrue(administratorCatalog.getFirst().administrator());
    }

    @Test
    void accessiblePoolQueryDoesNotDiscardEligibilityAfterFiveHundredPools() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pool Catalog Scale Owner");
        long memberId = 777701L;
        seedActiveMember(memberId, "Late Eligible Member");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Object[]> rows = new ArrayList<>();
        for (int index = 0; index < 501; index++) {
            rows.add(new Object[] {TENANT_ID, "Bulk pool " + String.format("%04d", index)});
        }
        jdbc.batchUpdate("INSERT INTO crm_contact_pool (tenant_id, name) VALUES (?, ?)", rows);
        jdbc.update("INSERT INTO crm_contact_pool (tenant_id, name) VALUES (?, ?)",
                TENANT_ID, "zz eligible pool");
        Long eligiblePoolId = jdbc.queryForObject(
                "SELECT id FROM crm_contact_pool WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, "zz eligible pool");
        jdbc.update("INSERT INTO crm_contact_pool_eligible_member "
                        + "(tenant_id, pool_id, actor_id) VALUES (?, ?, ?)",
                TENANT_ID, eligiblePoolId, memberId);

        List<ContactPoolCatalogQuery.Entry> accessible = poolCatalogQuery.findAccessible(
                new TenantId(TENANT_ID), new MemberId(memberId), null, false);

        assertEquals(List.of(eligiblePoolId), accessible.stream()
                .map(pool -> pool.id().getValue()).toList());
        assertFalse(accessible.getFirst().administrator());
    }

    @Test
    void concurrentCreationCannotExceedTheTenantPoolLimit() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pool Capacity Owner");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Object[]> rows = new ArrayList<>();
        for (int index = 0; index < 499; index++) {
            rows.add(new Object[] {TENANT_ID, "Capacity pool " + String.format("%04d", index)});
        }
        jdbc.batchUpdate("INSERT INTO crm_contact_pool (tenant_id, name) VALUES (?, ?)", rows);

        List<Integer> statuses = runConcurrently(
                () -> performPost("/api/v1/contact-pools", poolRequest("Capacity contender A"))
                        .andReturn().getResponse().getStatus(),
                () -> performPost("/api/v1/contact-pools", poolRequest("Capacity contender B"))
                        .andReturn().getResponse().getStatus());

        assertEquals(List.of(200, 422), statuses.stream().sorted().toList());
        assertEquals(500, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact_pool WHERE tenant_id = ? AND status <> 'DELETED'",
                Integer.class, TENANT_ID));
    }

    @Test
    void poolContactResponsesKeepVisibleParentInTheSamePool() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pool Parent Owner");
        CustomerResponse customer = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Pool-visible customer"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        ContactResponse parent = createContact("Pool parent");
        ContactResponse child = createContact("Pool child", parent.getId(), customer.getId());
        ContactPoolResponse pool = createPool("Parent relation pool", 0, 0);
        release(pool.getId(), parent);
        ContactResponse releasedChild = release(pool.getId(), child);
        assertEquals(customer.getId(), releasedChild.getCustomerId());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertNull(jdbc.queryForObject("SELECT owner_actor_id FROM crm_contact "
                + "WHERE tenant_id = ? AND id = ?", Long.class, TENANT_ID, parent.getId()));
        assertNull(jdbc.queryForObject("SELECT owner_actor_id FROM crm_contact "
                + "WHERE tenant_id = ? AND id = ?", Long.class, TENANT_ID, child.getId()));

        Pager<ContactResponse> page = fromJson(
                performGet("/api/v1/contact-pools/{poolId}/contacts", pool.getId())
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                new TypeReference<>() { });
        ContactResponse listedChild = page.getObjectList().stream()
                .filter(contact -> child.getId().equals(contact.getId()))
                .findFirst().orElseThrow();
        assertEquals(parent.getId(), listedChild.getParentContactId());
        assertEquals(customer.getId(), listedChild.getCustomerId());
        performGet("/api/v1/contact-pools/{poolId}/contacts/{contactId}",
                pool.getId(), child.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.parentContactId").value(parent.getId()))
                .andExpect(jsonPath("$.customerId").value(customer.getId()));
        ContactResponse claimed = first(performPost(
                "/api/v1/contact-pools/" + pool.getId() + "/contacts/claim",
                batch(releasedChild)).andExpect(status().isOk()));
        assertEquals(parent.getId(), claimed.getParentContactId());
        assertEquals(customer.getId(), claimed.getCustomerId());
    }

    @Test
    void poolSavedViewRelationsRequireVisibleCustomerTargets() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pool relation-view owner");
        long scopedActorId = 777703L;
        seedActiveMember(scopedActorId, "Pool relation-scoped reader");
        TenantRoleResponse reader = createRole("Pool relation-scoped reader",
                new RoleGrantRequest().setAuthorityCode("contacts:read").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("customers:read").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("contact-pools:read"),
                new RoleGrantRequest().setAuthorityCode("saved-views:write"));
        setFieldPolicy(reader.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("contact_name")
                        .setAccessLevel("READ").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode("customer")
                        .setAccessLevel("READ").setMaskType("NONE")));
        assignRole(reader.getId(), scopedActorId);

        CustomerResponse hiddenCustomer = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Pool hidden relation customer"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        ContactResponse contact = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Pool hidden relation contact")
                        .setOwnerActorId(scopedActorId).setCustomerId(hiddenCustomer.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        ContactPoolResponse pool = fromJson(performPost("/api/v1/contact-pools",
                new ContactPoolCreateRequest().setName("Scoped relation pool")
                        .setAdministratorActorIds(List.of(OWNER_ACTOR_ID))
                        .setEligibleActorIds(List.of(scopedActorId))
                        .setEligibleDepartmentIds(List.of()).setDailyClaimLimit(0)
                        .setPreviousOwnerCooldownDays(0).setAutomaticReleaseEnabled(false)
                        .setRules(List.of()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactPoolResponse.class);
        release(pool.getId(), contact);

        SavedViewResponse view = createSavedViewAs(scopedActorId,
                "Pool hidden customer relation", new FilterConditionRequest().setFieldCode("customer")
                        .setOperator("EQ").setValues(List.of(hiddenCustomer.getId().toString())));
        performAs(scopedActorId, TENANT_ID, get("/api/v1/contact-pools/{poolId}/contacts", pool.getId())
                        .param("savedViewId", view.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void releaseClaimLimitsAndPreviousOwnerCooldownAreDatabaseBacked() throws Exception {
        mockMvc.perform(get("/api/v1/contact-pools/catalog")).andExpect(status().isUnauthorized());
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pool Owner");
        ContactResponse contact = createContact("Pool account");
        ContactPoolResponse pool = createPool("Main pool", 1, 2);

        ContactResponse released = release(pool.getId(), contact);
        assertEquals("IN_POOL", released.getStatus());
        performGet("/api/v1/contacts/{contactId}", contact.getId())
                .andExpect(status().isNotFound());
        performGet("/api/v1/contact-pools/{poolId}/contacts", pool.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(contact.getId()));

        performPost("/api/v1/contact-pools/" + pool.getId() + "/contacts/claim",
                batch(released)).andExpect(status().is(422));
        ContactPoolResponse relaxed = fromJson(performPut(
                        "/api/v1/contact-pools/" + pool.getId(), update(pool, 1, 0))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactPoolResponse.class);
        ContactResponse claimed = first(performPost(
                "/api/v1/contact-pools/" + pool.getId() + "/contacts/claim", batch(released))
                .andExpect(status().isOk()));
        assertEquals("ACTIVE", claimed.getStatus());
        assertEquals(OWNER_ACTOR_ID, claimed.getOwnerActorId());

        ContactResponse releasedAgain = release(relaxed.getId(), claimed);
        performPost("/api/v1/contact-pools/" + pool.getId() + "/contacts/claim",
                batch(releasedAgain)).andExpect(status().is(422));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact_pool_claim_history "
                        + "WHERE tenant_id = ? AND pool_id = ? AND is_assigned = 0",
                Integer.class, TENANT_ID, pool.getId()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact_pool_membership WHERE tenant_id = ? AND pool_id = ?",
                Integer.class, TENANT_ID, pool.getId()));
    }

    @Test
    void transferAssignmentAndCrossTenantVisibilityFailClosed() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pool Transfer Owner");
        long assignee = 777777L;
        seedActiveMember(assignee, "Pool Assignee");
        ContactResponse contact = createContact("Transfer account");
        ContactPoolResponse source = createPool("Source pool", 0, 0);
        ContactPoolResponse target = createPool("Target pool", 0, 0);
        ContactResponse released = release(source.getId(), contact);

        performPost("/api/v1/contact-pools/" + source.getId() + "/transfer",
                new ContactPoolTransferRequest().setTargetPoolId(target.getId()))
                .andExpect(status().isOk());
        performGet("/api/v1/contact-pools/{poolId}/contacts", source.getId())
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(0));
        performGet("/api/v1/contact-pools/{poolId}/contacts", target.getId())
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1));
        performPut("/api/v1/contact-pools/" + target.getId() + "/status",
                new ContactPoolStatusUpdateRequest().setVersion(target.getVersion()).setActive(false))
                .andExpect(status().is(422));
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                delete("/api/v1/contact-pools/{poolId}", target.getId())
                        .param("version", target.getVersion().toString()))
                .andExpect(status().is(422));

        ContactResponse assigned = first(performPost(
                "/api/v1/contact-pools/" + target.getId() + "/contacts/assign",
                new ContactPoolAssignRequest().setOwnerActorId(assignee)
                        .setContacts(batch(released).getContacts()))
                .andExpect(status().isOk()));
        assertEquals(assignee, assigned.getOwnerActorId());

        long otherActor = 666666L;
        long otherTenant = 666001L;
        bootstrap(otherActor, otherTenant, "Other Pool Owner");
        performAs(otherActor, otherTenant, get("/api/v1/contact-pools/catalog"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        performAs(otherActor, otherTenant,
                get("/api/v1/contact-pools/{poolId}/contacts", target.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentClaimsProduceOneOwnerAndOneClaimRecord() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pool Concurrency Owner");
        ContactResponse contact = createContact("Concurrent pool account");
        ContactPoolResponse pool = createPool("Concurrent pool", 0, 0);
        ContactResponse released = release(pool.getId(), contact);
        ContactPoolContactBatchRequest request = batch(released);

        List<Integer> statuses = runConcurrently(
                () -> claimStatus(pool.getId(), request),
                () -> claimStatus(pool.getId(), request));
        assertEquals(List.of(200, 404), statuses.stream().sorted().toList());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact_pool_membership WHERE tenant_id = ?",
                Integer.class, TENANT_ID));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact_pool_claim_history WHERE tenant_id = ?",
                Integer.class, TENANT_ID));
        assertEquals(OWNER_ACTOR_ID, jdbc.queryForObject(
                "SELECT owner_actor_id FROM crm_contact WHERE tenant_id = ? AND id = ?",
                Long.class, TENANT_ID, contact.getId()));
    }

    @Test
    void automaticReleaseIsBoundedAndRepeatable() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pool Automation Owner");
        ContactResponse contact = createContact("Dormant pool account");
        ContactPoolCreateRequest request = new ContactPoolCreateRequest().setName("Automatic pool")
                .setAdministratorActorIds(List.of(OWNER_ACTOR_ID))
                .setEligibleActorIds(List.of(OWNER_ACTOR_ID))
                .setEligibleDepartmentIds(List.of())
                .setDailyClaimLimit(0).setPreviousOwnerCooldownDays(0)
                .setAutomaticReleaseEnabled(true)
                .setRules(List.of(new ContactPoolRuleRequest()
                        .setType("STALE_UPDATE").setInactiveDays(2)));
        ContactPoolResponse pool = fromJson(performPost("/api/v1/contact-pools", request)
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactPoolResponse.class);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_automation_schedule "
                        + "WHERE tenant_id = ? AND kind = 'CONTACT_POOL_RELEASE' AND status = 'ACTIVE'",
                Integer.class, TENANT_ID));
        jdbc.update("UPDATE crm_contact SET updated_time = ? WHERE tenant_id = ? AND id = ?",
                LocalDateTime.now().minusDays(10), TENANT_ID, contact.getId());
        AuthenticatedCaller caller = new AuthenticatedCaller(OWNER_ACTOR_ID, TENANT_ID,
                Set.of(AuthorityCatalog.CONTACT_POOLS_MANAGE));

        Result<Integer> first = automationService.run(caller, LocalDateTime.now(), 10);
        Result<Integer> second = automationService.run(caller, LocalDateTime.now(), 10);

        assertTrue(first.isSuccess());
        assertEquals(1, first.getData());
        assertTrue(second.isSuccess());
        assertEquals(0, second.getData());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact_pool_membership "
                        + "WHERE tenant_id = ? AND pool_id = ? AND contact_id = ?",
                Integer.class, TENANT_ID, pool.getId(), contact.getId()));
        assertEquals("IN_POOL", jdbc.queryForObject(
                "SELECT status FROM crm_contact WHERE tenant_id = ? AND id = ?",
                String.class, TENANT_ID, contact.getId()));
        assertEquals(pool.getId(), jdbc.queryForObject(
                "SELECT last_pool_id FROM crm_contact_pool_automation_cursor WHERE tenant_id = ?",
                Long.class, TENANT_ID));
    }

    private ContactPoolResponse createPool(String name, int claimLimit, int cooldown) throws Exception {
        ContactPoolCreateRequest request = poolRequest(name)
                .setDailyClaimLimit(claimLimit).setPreviousOwnerCooldownDays(cooldown);
        return fromJson(performPost("/api/v1/contact-pools", request)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactPoolResponse.class);
    }

    private ContactPoolCreateRequest poolRequest(String name) {
        return new ContactPoolCreateRequest().setName(name)
                .setAdministratorActorIds(List.of(OWNER_ACTOR_ID))
                .setEligibleActorIds(List.of(OWNER_ACTOR_ID))
                .setEligibleDepartmentIds(List.of())
                .setDailyClaimLimit(0).setPreviousOwnerCooldownDays(0)
                .setAutomaticReleaseEnabled(false).setRules(List.of());
    }

    private ContactPoolUpdateRequest update(
            ContactPoolResponse pool, int claimLimit, int cooldown) {
        return new ContactPoolUpdateRequest().setVersion(pool.getVersion()).setName(pool.getName())
                .setAdministratorActorIds(pool.getAdministratorActorIds())
                .setEligibleActorIds(pool.getEligibleActorIds())
                .setEligibleDepartmentIds(pool.getEligibleDepartmentIds())
                .setDailyClaimLimit(claimLimit).setPreviousOwnerCooldownDays(cooldown)
                .setAutomaticReleaseEnabled(false).setRules(List.of());
    }

    private ContactResponse createContact(String name) throws Exception {
        return createContact(name, null);
    }

    private ContactResponse createContact(String name, Long parentContactId) throws Exception {
        return createContact(name, parentContactId, null);
    }

    private ContactResponse createContact(String name, Long parentContactId, Long customerId) throws Exception {
        ContactCreateRequest request = new ContactCreateRequest().setName(name)
                .setParentContactId(parentContactId).setCustomerId(customerId);
        return fromJson(performPost("/api/v1/contacts", request)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
    }

    private TenantRoleResponse createRole(String name, RoleGrantRequest... grants) throws Exception {
        return fromJson(performPost("/api/v1/authorization/roles",
                new RoleCreateRequest().setName(name).setGrants(List.of(grants)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                TenantRoleResponse.class);
    }

    private void setFieldPolicy(Long roleId, List<RoleFieldAccessRequest> accesses) throws Exception {
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/fields/roles/{roleId}/objects/CONTACT/access", roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleFieldPolicyReplaceRequest().setAccesses(accesses))))
                .andExpect(status().isOk());
    }

    private void assignRole(Long roleId, long actorId) throws Exception {
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                post("/api/v1/authorization/roles/{roleId}/assignments", roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleAssignmentCreateRequest()
                                .setActorIds(List.of(actorId)))))
                .andExpect(status().isOk());
    }

    private SavedViewResponse createSavedViewAs(
            long actorId, String name, FilterConditionRequest condition) throws Exception {
        return fromJson(performAs(actorId, TENANT_ID, post("/api/v1/saved-views")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new SavedViewCreateRequest().setObjectType("CONTACT").setName(name)
                                .setConditions(List.of(condition)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SavedViewResponse.class);
    }

    private ContactResponse release(Long poolId, ContactResponse contact) throws Exception {
        return first(performPost("/api/v1/contact-pools/" + poolId + "/contacts/release",
                batch(contact)).andExpect(status().isOk()));
    }

    private ContactPoolContactBatchRequest batch(ContactResponse contact) {
        return new ContactPoolContactBatchRequest().setContacts(List.of(
                new ContactVersionRequest().setContactId(contact.getId())
                        .setVersion(contact.getVersion())));
    }

    private ContactResponse first(ResultActions action) throws Exception {
        List<ContactResponse> values = fromJson(
                action.andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        return values.getFirst();
    }

    private int claimStatus(Long poolId, ContactPoolContactBatchRequest request) throws Exception {
        return performPost("/api/v1/contact-pools/" + poolId + "/contacts/claim", request)
                .andReturn().getResponse().getStatus();
    }

    @SafeVarargs
    private final List<Integer> runConcurrently(java.util.concurrent.Callable<Integer>... tasks)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
        try {
            List<Future<Integer>> futures = Arrays.stream(tasks).map(task -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("claim start timeout");
                return task.call();
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<Integer> statuses = new java.util.ArrayList<>();
            for (Future<Integer> future : futures) statuses.add(future.get(10, TimeUnit.SECONDS));
            return List.copyOf(statuses);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void bootstrap(long actorId, long tenantId, String displayName) throws Exception {
        performAs(actorId, tenantId, post("/api/v1/organization/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName(displayName))))
                .andExpect(status().isOk());
    }

    private void seedActiveMember(long actorId, String displayName) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                actorId, displayName);
        jdbc.update("INSERT INTO organization_membership "
                        + "(tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, actorId);
        jdbc.update("INSERT INTO authorization_role_assignment (tenant_id, actor_id, role_id) "
                        + "SELECT tenant_id, ?, id FROM authorization_role "
                        + "WHERE tenant_id = ? AND system_key = 'TENANT_MEMBER'",
                actorId, TENANT_ID);
    }

    private ResultActions performAs(
            long actorId, long tenantId, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("X-Dev-User-Id", actorId)
                .header("X-Dev-Tenant-Id", tenantId));
    }

    @Override
    protected void initTestData() {
        clearPoolData();
    }

    @Override
    protected void clearTestData() {
        clearPoolData();
    }

    private void clearPoolData() {
        executeSql("DELETE FROM crm_contact_pool_automation_cursor");
        executeSql("DELETE FROM crm_contact_pool_claim_history");
        executeSql("DELETE FROM crm_contact_pool_membership");
        executeSql("DELETE FROM crm_contact_pool_rule");
        executeSql("DELETE FROM crm_contact_pool_eligible_department");
        executeSql("DELETE FROM crm_contact_pool_eligible_member");
        executeSql("DELETE FROM crm_contact_pool_administrator");
        executeSql("DELETE FROM crm_contact_pool");
        executeSql("DELETE FROM crm_contact_star");
        executeSql("DELETE FROM crm_contact_unique_field_value");
        executeSql("DELETE FROM crm_contact_field_value");
        executeSql("DELETE FROM crm_contact_collaborator");
        executeSql("DELETE FROM crm_contact_tag");
        executeSql("DELETE FROM crm_contact_address");
        executeSql("UPDATE crm_contact SET parent_contact_id = NULL");
        executeSql("DELETE FROM crm_contact");
        executeSql("DELETE FROM crm_contact_guard");
        clearCustomers();
        executeSql("DELETE FROM embedded_page");
        executeSql("DELETE FROM saved_view");
        executeSql("DELETE FROM role_field_access");
        executeSql("DELETE FROM role_field_policy");
        executeSql("DELETE FROM field_layout_preference");
        executeSql("DELETE FROM custom_field_definition");
        executeSql("DELETE FROM customization_object_guard");
        executeSql("DELETE FROM navigation_preference");
        executeSql("DELETE FROM authorization_role_assignment");
        executeSql("DELETE FROM authorization_role_grant");
        executeSql("DELETE FROM authorization_role");
        executeSql("DELETE FROM organization_membership_request");
        executeSql("DELETE FROM organization_membership");
        executeSql("DELETE FROM organization_department");
        executeSql("DELETE FROM organization_member_profile");
        executeSql("DELETE FROM organization_tenant");
    }

    private void clearCustomers() {
        executeSql("DELETE FROM crm_customer_star");
        executeSql("DELETE FROM crm_customer_unique_field_value");
        executeSql("DELETE FROM crm_customer_field_value");
        executeSql("DELETE FROM crm_customer_collaborator");
        executeSql("DELETE FROM crm_customer_tag");
        executeSql("DELETE FROM crm_customer_address");
        executeSql("UPDATE crm_customer SET parent_customer_id = NULL");
        executeSql("DELETE FROM crm_customer");
        executeSql("DELETE FROM crm_customer_guard");
    }
}
