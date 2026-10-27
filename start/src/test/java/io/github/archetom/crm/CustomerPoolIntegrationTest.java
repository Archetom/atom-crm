package io.github.archetom.crm;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CustomerCreateRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolAssignRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolCreateRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolCustomerBatchRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolRuleRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolTransferRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerVersionRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.response.CustomerPoolResponse;
import io.github.archetom.crm.api.dto.response.CustomerResponse;
import io.github.archetom.crm.application.port.out.CustomerPoolCatalogQuery;
import io.github.archetom.crm.application.service.CustomerPoolAutomationService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RequiresDatabase
class CustomerPoolIntegrationTest extends BaseIntegrationTest {

    private static final long OWNER_ACTOR_ID = 888888L;
    private static final long TENANT_ID = 999999L;

    @Autowired
    private CustomerPoolAutomationService automationService;
    @Autowired
    private CustomerPoolCatalogQuery poolCatalogQuery;

    @Test
    void catalogIncludesTenantOwnerPoolWithoutExplicitEligibility() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pool Catalog Owner");
        long poolAdministrator = 777702L;
        seedActiveMember(poolAdministrator, "Pool Administrator");
        CustomerPoolCreateRequest request = new CustomerPoolCreateRequest()
                .setName("Owner-only catalog pool")
                .setAdministratorActorIds(List.of(poolAdministrator))
                .setEligibleActorIds(List.of(poolAdministrator))
                .setEligibleDepartmentIds(List.of()).setDailyClaimLimit(0)
                .setPreviousOwnerCooldownDays(0).setAutomaticReleaseEnabled(false)
                .setRules(List.of());
        CustomerPoolResponse pool = fromJson(performPost("/api/v1/customer-pools", request)
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerPoolResponse.class);

        performGet("/api/v1/customer-pools/catalog").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(pool.getId()))
                .andExpect(jsonPath("$[0].administrator").value(true));
        List<CustomerPoolCatalogQuery.Entry> administratorCatalog = poolCatalogQuery.findAccessible(
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
        jdbc.batchUpdate("INSERT INTO crm_customer_pool (tenant_id, name) VALUES (?, ?)", rows);
        jdbc.update("INSERT INTO crm_customer_pool (tenant_id, name) VALUES (?, ?)",
                TENANT_ID, "zz eligible pool");
        Long eligiblePoolId = jdbc.queryForObject(
                "SELECT id FROM crm_customer_pool WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, "zz eligible pool");
        jdbc.update("INSERT INTO crm_customer_pool_eligible_member "
                        + "(tenant_id, pool_id, actor_id) VALUES (?, ?, ?)",
                TENANT_ID, eligiblePoolId, memberId);

        List<CustomerPoolCatalogQuery.Entry> accessible = poolCatalogQuery.findAccessible(
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
        jdbc.batchUpdate("INSERT INTO crm_customer_pool (tenant_id, name) VALUES (?, ?)", rows);

        List<Integer> statuses = runConcurrently(
                () -> performPost("/api/v1/customer-pools", poolRequest("Capacity contender A"))
                        .andReturn().getResponse().getStatus(),
                () -> performPost("/api/v1/customer-pools", poolRequest("Capacity contender B"))
                        .andReturn().getResponse().getStatus());

        assertEquals(List.of(200, 422), statuses.stream().sorted().toList());
        assertEquals(500, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_customer_pool WHERE tenant_id = ? AND status <> 'DELETED'",
                Integer.class, TENANT_ID));
    }

    @Test
    void poolCustomerResponsesKeepVisibleParentInTheSamePool() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pool Parent Owner");
        CustomerResponse parent = createCustomer("Pool parent");
        CustomerResponse child = createCustomer("Pool child", parent.getId());
        CustomerPoolResponse pool = createPool("Parent relation pool", 0, 0);
        release(pool.getId(), parent);
        CustomerResponse releasedChild = release(pool.getId(), child);

        Pager<CustomerResponse> page = fromJson(
                performGet("/api/v1/customer-pools/{poolId}/customers", pool.getId())
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                new TypeReference<>() { });
        CustomerResponse listedChild = page.getObjectList().stream()
                .filter(customer -> child.getId().equals(customer.getId()))
                .findFirst().orElseThrow();
        assertEquals(parent.getId(), listedChild.getParentCustomerId());
        performGet("/api/v1/customer-pools/{poolId}/customers/{customerId}",
                pool.getId(), child.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.parentCustomerId").value(parent.getId()));
        CustomerResponse claimed = first(performPost(
                "/api/v1/customer-pools/" + pool.getId() + "/customers/claim",
                batch(releasedChild)).andExpect(status().isOk()));
        assertEquals(parent.getId(), claimed.getParentCustomerId());
    }

    @Test
    void releaseClaimLimitsAndPreviousOwnerCooldownAreDatabaseBacked() throws Exception {
        mockMvc.perform(get("/api/v1/customer-pools/catalog")).andExpect(status().isUnauthorized());
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pool Owner");
        CustomerResponse customer = createCustomer("Pool account");
        CustomerPoolResponse pool = createPool("Main pool", 1, 2);

        CustomerResponse released = release(pool.getId(), customer);
        assertEquals("IN_POOL", released.getStatus());
        performGet("/api/v1/customers/{customerId}", customer.getId())
                .andExpect(status().isNotFound());
        performGet("/api/v1/customer-pools/{poolId}/customers", pool.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(customer.getId()));

        performPost("/api/v1/customer-pools/" + pool.getId() + "/customers/claim",
                batch(released)).andExpect(status().is(422));
        CustomerPoolResponse relaxed = fromJson(performPut(
                        "/api/v1/customer-pools/" + pool.getId(), update(pool, 1, 0))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerPoolResponse.class);
        CustomerResponse claimed = first(performPost(
                "/api/v1/customer-pools/" + pool.getId() + "/customers/claim", batch(released))
                .andExpect(status().isOk()));
        assertEquals("ACTIVE", claimed.getStatus());
        assertEquals(OWNER_ACTOR_ID, claimed.getOwnerActorId());

        CustomerResponse releasedAgain = release(relaxed.getId(), claimed);
        performPost("/api/v1/customer-pools/" + pool.getId() + "/customers/claim",
                batch(releasedAgain)).andExpect(status().is(422));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_customer_pool_claim_history "
                        + "WHERE tenant_id = ? AND pool_id = ? AND is_assigned = 0",
                Integer.class, TENANT_ID, pool.getId()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_customer_pool_membership WHERE tenant_id = ? AND pool_id = ?",
                Integer.class, TENANT_ID, pool.getId()));
    }

    @Test
    void transferAssignmentAndCrossTenantVisibilityFailClosed() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pool Transfer Owner");
        long assignee = 777777L;
        seedActiveMember(assignee, "Pool Assignee");
        CustomerResponse customer = createCustomer("Transfer account");
        CustomerPoolResponse source = createPool("Source pool", 0, 0);
        CustomerPoolResponse target = createPool("Target pool", 0, 0);
        CustomerResponse released = release(source.getId(), customer);

        performPost("/api/v1/customer-pools/" + source.getId() + "/transfer",
                new CustomerPoolTransferRequest().setTargetPoolId(target.getId()))
                .andExpect(status().isOk());
        performGet("/api/v1/customer-pools/{poolId}/customers", source.getId())
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(0));
        performGet("/api/v1/customer-pools/{poolId}/customers", target.getId())
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1));
        performPut("/api/v1/customer-pools/" + target.getId() + "/status",
                new CustomerPoolStatusUpdateRequest().setVersion(target.getVersion()).setActive(false))
                .andExpect(status().is(422));
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                delete("/api/v1/customer-pools/{poolId}", target.getId())
                        .param("version", target.getVersion().toString()))
                .andExpect(status().is(422));

        CustomerResponse assigned = first(performPost(
                "/api/v1/customer-pools/" + target.getId() + "/customers/assign",
                new CustomerPoolAssignRequest().setOwnerActorId(assignee)
                        .setCustomers(batch(released).getCustomers()))
                .andExpect(status().isOk()));
        assertEquals(assignee, assigned.getOwnerActorId());

        long otherActor = 666666L;
        long otherTenant = 666001L;
        bootstrap(otherActor, otherTenant, "Other Pool Owner");
        performAs(otherActor, otherTenant, get("/api/v1/customer-pools/catalog"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        performAs(otherActor, otherTenant,
                get("/api/v1/customer-pools/{poolId}/customers", target.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentClaimsProduceOneOwnerAndOneClaimRecord() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pool Concurrency Owner");
        CustomerResponse customer = createCustomer("Concurrent pool account");
        CustomerPoolResponse pool = createPool("Concurrent pool", 0, 0);
        CustomerResponse released = release(pool.getId(), customer);
        CustomerPoolCustomerBatchRequest request = batch(released);

        List<Integer> statuses = runConcurrently(
                () -> claimStatus(pool.getId(), request),
                () -> claimStatus(pool.getId(), request));
        assertEquals(List.of(200, 404), statuses.stream().sorted().toList());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_customer_pool_membership WHERE tenant_id = ?",
                Integer.class, TENANT_ID));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_customer_pool_claim_history WHERE tenant_id = ?",
                Integer.class, TENANT_ID));
        assertEquals(OWNER_ACTOR_ID, jdbc.queryForObject(
                "SELECT owner_actor_id FROM crm_customer WHERE tenant_id = ? AND id = ?",
                Long.class, TENANT_ID, customer.getId()));
    }

    @Test
    void automaticReleaseIsBoundedAndRepeatable() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Pool Automation Owner");
        CustomerResponse customer = createCustomer("Dormant pool account");
        CustomerPoolCreateRequest request = new CustomerPoolCreateRequest().setName("Automatic pool")
                .setAdministratorActorIds(List.of(OWNER_ACTOR_ID))
                .setEligibleActorIds(List.of(OWNER_ACTOR_ID))
                .setEligibleDepartmentIds(List.of())
                .setDailyClaimLimit(0).setPreviousOwnerCooldownDays(0)
                .setAutomaticReleaseEnabled(true)
                .setRules(List.of(new CustomerPoolRuleRequest()
                        .setType("STALE_UPDATE").setInactiveDays(2)));
        CustomerPoolResponse pool = fromJson(performPost("/api/v1/customer-pools", request)
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerPoolResponse.class);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_automation_schedule "
                        + "WHERE tenant_id = ? AND kind = 'CUSTOMER_POOL_RELEASE' AND status = 'ACTIVE'",
                Integer.class, TENANT_ID));
        jdbc.update("UPDATE crm_customer SET updated_time = ? WHERE tenant_id = ? AND id = ?",
                LocalDateTime.now().minusDays(10), TENANT_ID, customer.getId());
        AuthenticatedCaller caller = new AuthenticatedCaller(OWNER_ACTOR_ID, TENANT_ID,
                Set.of(AuthorityCatalog.CUSTOMER_POOLS_MANAGE));

        Result<Integer> first = automationService.run(caller, LocalDateTime.now(), 10);
        Result<Integer> second = automationService.run(caller, LocalDateTime.now(), 10);

        assertTrue(first.isSuccess());
        assertEquals(1, first.getData());
        assertTrue(second.isSuccess());
        assertEquals(0, second.getData());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_customer_pool_membership "
                        + "WHERE tenant_id = ? AND pool_id = ? AND customer_id = ?",
                Integer.class, TENANT_ID, pool.getId(), customer.getId()));
        assertEquals("IN_POOL", jdbc.queryForObject(
                "SELECT status FROM crm_customer WHERE tenant_id = ? AND id = ?",
                String.class, TENANT_ID, customer.getId()));
        assertEquals(pool.getId(), jdbc.queryForObject(
                "SELECT last_pool_id FROM crm_customer_pool_automation_cursor WHERE tenant_id = ?",
                Long.class, TENANT_ID));
    }

    private CustomerPoolResponse createPool(String name, int claimLimit, int cooldown) throws Exception {
        CustomerPoolCreateRequest request = poolRequest(name)
                .setDailyClaimLimit(claimLimit).setPreviousOwnerCooldownDays(cooldown);
        return fromJson(performPost("/api/v1/customer-pools", request)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerPoolResponse.class);
    }

    private CustomerPoolCreateRequest poolRequest(String name) {
        return new CustomerPoolCreateRequest().setName(name)
                .setAdministratorActorIds(List.of(OWNER_ACTOR_ID))
                .setEligibleActorIds(List.of(OWNER_ACTOR_ID))
                .setEligibleDepartmentIds(List.of())
                .setDailyClaimLimit(0).setPreviousOwnerCooldownDays(0)
                .setAutomaticReleaseEnabled(false).setRules(List.of());
    }

    private CustomerPoolUpdateRequest update(
            CustomerPoolResponse pool, int claimLimit, int cooldown) {
        return new CustomerPoolUpdateRequest().setVersion(pool.getVersion()).setName(pool.getName())
                .setAdministratorActorIds(pool.getAdministratorActorIds())
                .setEligibleActorIds(pool.getEligibleActorIds())
                .setEligibleDepartmentIds(pool.getEligibleDepartmentIds())
                .setDailyClaimLimit(claimLimit).setPreviousOwnerCooldownDays(cooldown)
                .setAutomaticReleaseEnabled(false).setRules(List.of());
    }

    private CustomerResponse createCustomer(String name) throws Exception {
        return createCustomer(name, null);
    }

    private CustomerResponse createCustomer(String name, Long parentCustomerId) throws Exception {
        CustomerCreateRequest request = new CustomerCreateRequest().setName(name)
                .setParentCustomerId(parentCustomerId);
        return fromJson(performPost("/api/v1/customers", request)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
    }

    private CustomerResponse release(Long poolId, CustomerResponse customer) throws Exception {
        return first(performPost("/api/v1/customer-pools/" + poolId + "/customers/release",
                batch(customer)).andExpect(status().isOk()));
    }

    private CustomerPoolCustomerBatchRequest batch(CustomerResponse customer) {
        return new CustomerPoolCustomerBatchRequest().setCustomers(List.of(
                new CustomerVersionRequest().setCustomerId(customer.getId())
                        .setVersion(customer.getVersion())));
    }

    private CustomerResponse first(ResultActions action) throws Exception {
        List<CustomerResponse> values = fromJson(
                action.andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        return values.getFirst();
    }

    private int claimStatus(Long poolId, CustomerPoolCustomerBatchRequest request) throws Exception {
        return performPost("/api/v1/customer-pools/" + poolId + "/customers/claim", request)
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
        executeSql("DELETE FROM crm_customer_pool_automation_cursor");
        executeSql("DELETE FROM crm_customer_pool_claim_history");
        executeSql("DELETE FROM crm_customer_pool_membership");
        executeSql("DELETE FROM crm_customer_pool_rule");
        executeSql("DELETE FROM crm_customer_pool_eligible_department");
        executeSql("DELETE FROM crm_customer_pool_eligible_member");
        executeSql("DELETE FROM crm_customer_pool_administrator");
        executeSql("DELETE FROM crm_customer_pool");
        executeSql("DELETE FROM crm_customer_star");
        executeSql("DELETE FROM crm_customer_unique_field_value");
        executeSql("DELETE FROM crm_customer_field_value");
        executeSql("DELETE FROM crm_customer_collaborator");
        executeSql("DELETE FROM crm_customer_tag");
        executeSql("DELETE FROM crm_customer_address");
        executeSql("UPDATE crm_customer SET parent_customer_id = NULL");
        executeSql("DELETE FROM crm_customer");
        executeSql("DELETE FROM crm_customer_guard");
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
}
