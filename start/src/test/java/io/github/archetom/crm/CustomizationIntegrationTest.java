package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.EmbeddedPageCreateRequest;
import io.github.archetom.crm.api.dto.request.EmbeddedPageUpdateRequest;
import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldLayoutUpdateRequest;
import io.github.archetom.crm.api.dto.request.FieldUpdateRequest;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.RoleAssignmentCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldAccessRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldPolicyReplaceRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantRequest;
import io.github.archetom.crm.api.dto.request.SavedViewCreateRequest;
import io.github.archetom.crm.api.dto.request.SavedViewConditionRequest;
import io.github.archetom.crm.api.dto.response.EmbeddedPageResponse;
import io.github.archetom.crm.api.dto.response.FieldDefinitionResponse;
import io.github.archetom.crm.api.dto.response.FieldLayoutResponse;
import io.github.archetom.crm.api.dto.response.RoleFieldPolicyResponse;
import io.github.archetom.crm.api.dto.response.SavedViewResponse;
import io.github.archetom.crm.api.dto.response.TenantRoleResponse;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.FieldLayoutPreferenceRepository;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FieldDefinitionId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RequiresDatabase
class CustomizationIntegrationTest extends BaseIntegrationTest {

    private static final long OWNER_ACTOR_ID = 888888L;
    private static final long TENANT_ID = 999999L;

    @Autowired
    private EffectiveFieldAccessResolver fieldAccessResolver;

    @Autowired
    private CustomizationObjectLock objectLock;

    @Autowired
    private FieldDefinitionRepository fieldRepository;

    @Autowired
    private FieldLayoutPreferenceRepository layoutRepository;

    @Autowired
    private SavedViewRepository viewRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void fieldsLayoutsAndRolePoliciesStayTenantScopedAndFailClosed() throws Exception {
        mockMvc.perform(get("/api/v1/fields/objects")).andExpect(status().isUnauthorized());
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customization Owner");

        FieldDefinitionResponse field = createTextField("Priority");
        assertEquals(FieldAccessLevel.WRITE, fieldAccessResolver.resolve(
                new TenantId(TENANT_ID), new MemberId(OWNER_ACTOR_ID), BusinessObjectType.CUSTOMER)
                .get(new FieldCode(field.getCode())).accessLevel());
        FieldLayoutResponse layout = fromJson(performPut("/api/v1/fields/objects/CUSTOMER/layout",
                new FieldLayoutUpdateRequest().setOrderedCodes(List.of(field.getCode()))
                        .setPinnedCodes(List.of(field.getCode()))
                        .setColumnWidths(Map.of(field.getCode(), 180)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldLayoutResponse.class);
        assertEquals(List.of(field.getCode()), layout.getOrderedCodes());
        performPut("/api/v1/fields/objects/CUSTOMER/layout",
                new FieldLayoutUpdateRequest().setVersion(0L)
                        .setOrderedCodes(List.of(field.getCode())))
                .andExpect(status().isOk());
        performPut("/api/v1/fields/objects/CUSTOMER/layout",
                new FieldLayoutUpdateRequest().setVersion(0L)
                        .setOrderedCodes(List.of(field.getCode())))
                .andExpect(status().isConflict());

        TenantRoleResponse reader = createRole("Field Readers", "fields:read");
        RoleFieldPolicyResponse policy = fromJson(performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/fields/roles/{roleId}/objects/CUSTOMER/access", reader.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleFieldPolicyReplaceRequest().setAccesses(List.of(
                                new RoleFieldAccessRequest().setFieldCode(field.getCode())
                                        .setAccessLevel("READ").setMaskType("PARTIAL"))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                RoleFieldPolicyResponse.class);
        assertEquals("PARTIAL", policy.getAccesses().getFirst().getMaskType());

        long targetActorId = 777777L;
        seedActiveMember(targetActorId, "Field Reader");
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                post("/api/v1/authorization/roles/{roleId}/assignments", reader.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleAssignmentCreateRequest()
                                .setActorIds(List.of(targetActorId)))))
                .andExpect(status().isOk());
        assertEquals(FieldAccessLevel.READ, fieldAccessResolver.resolve(
                new TenantId(TENANT_ID), new MemberId(targetActorId), BusinessObjectType.CUSTOMER)
                .get(new FieldCode(field.getCode())).accessLevel());
        performAs(targetActorId, TENANT_ID, get("/api/v1/fields/objects/CUSTOMER"))
                .andExpect(status().isOk());
        performAs(targetActorId, TENANT_ID, post("/api/v1/fields/objects/CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new FieldCreateRequest().setDisplayName("Blocked")
                        .setFieldType("TEXT"))))
                .andExpect(status().isForbidden());
        new JdbcTemplate(dataSource).update(
                "UPDATE organization_membership SET status = 'SUSPENDED' "
                        + "WHERE tenant_id = ? AND actor_id = ?", TENANT_ID, targetActorId);
        assertEquals(FieldAccessLevel.HIDDEN, fieldAccessResolver.resolve(
                new TenantId(TENANT_ID), new MemberId(targetActorId), BusinessObjectType.CUSTOMER)
                .get(new FieldCode(field.getCode())).accessLevel());

        long otherActorId = 666666L;
        long otherTenantId = 666001L;
        bootstrap(otherActorId, otherTenantId, "Other Owner");
        performAs(otherActorId, otherTenantId,
                put("/api/v1/fields/objects/CUSTOMER/{fieldId}", field.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new FieldUpdateRequest().setVersion(field.getVersion())
                                .setDisplayName("Other").setOptions(List.of()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void savedViewsAndEmbeddedPagesUseTypedFiltersAndSafeTemplates() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customization Owner");
        performGet("/api/v1/embedded-pages/configuration-options")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectTypes[*].code").value(hasItems("CUSTOMER", "OA_REQUEST")))
                .andExpect(jsonPath("$.platforms").value(hasItems("WEB", "MOBILE")))
                .andExpect(jsonPath("$.statuses").value(hasItems("ACTIVE", "INACTIVE")));
        FieldDefinitionResponse field = createTextField("Segment");

        SavedViewResponse view = fromJson(performPost("/api/v1/saved-views",
                new SavedViewCreateRequest().setObjectType("CUSTOMER").setName("Enterprise")
                        .setConditions(List.of(new SavedViewConditionRequest()
                                .setFieldCode(field.getCode()).setOperator("EQ")
                                .setValues(List.of("enterprise")).setExposed(true))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SavedViewResponse.class);
        assertTrue(view.getConditions().getFirst().getExposed());
        SavedViewResponse defaultView = fromJson(performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/saved-views/{viewId}/default", view.getId())
                        .param("version", view.getVersion().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SavedViewResponse.class);
        assertTrue(defaultView.getDefaultView());
        SavedViewResponse secondView = fromJson(performPost("/api/v1/saved-views",
                new SavedViewCreateRequest().setObjectType("CUSTOMER").setName("Growth")
                        .setConditions(List.of(new FilterConditionRequest()
                                .setFieldCode(field.getCode()).setOperator("EQ")
                                .setValues(List.of("growth")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SavedViewResponse.class);
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/saved-views/{viewId}/default", secondView.getId())
                        .param("version", secondView.getVersion().toString()))
                .andExpect(status().isOk());
        List<SavedViewResponse> views = fromJson(performGetWithParams("/api/v1/saved-views",
                        Map.of("objectType", "CUSTOMER"))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                new TypeReference<>() { });
        assertEquals(2, views.size());
        assertEquals(1, views.stream().filter(SavedViewResponse::getDefaultView).count());

        long otherMemberId = 555555L;
        seedActiveMember(otherMemberId, "Saved View Member");
        TenantRoleResponse viewRole = createRole("Saved View Members",
                "saved-views:read", "saved-views:write");
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                post("/api/v1/authorization/roles/{roleId}/assignments", viewRole.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleAssignmentCreateRequest()
                                .setActorIds(List.of(otherMemberId)))))
                .andExpect(status().isOk());
        performAs(otherMemberId, TENANT_ID, get("/api/v1/saved-views")
                .param("objectType", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        performAs(otherMemberId, TENANT_ID, delete("/api/v1/saved-views/{viewId}", view.getId())
                .param("version", defaultView.getVersion().toString()))
                .andExpect(status().isNotFound());

        performPost("/api/v1/embedded-pages", new EmbeddedPageCreateRequest()
                .setObjectType("CUSTOMER").setPlatform("WEB").setName("Unsafe")
                .setUrlTemplate("http://example.invalid/{recordId}"))
                .andExpect(status().is(422));
        EmbeddedPageResponse page = fromJson(performPost("/api/v1/embedded-pages",
                new EmbeddedPageCreateRequest().setObjectType("CUSTOMER").setPlatform("WEB")
                        .setName("Account").setUrlTemplate(
                                "https://example.invalid/accounts/{recordId}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                EmbeddedPageResponse.class);
        EmbeddedPageResponse active = fromJson(performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/embedded-pages/{pageId}", page.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new EmbeddedPageUpdateRequest().setVersion(page.getVersion())
                                .setName(page.getName()).setUrlTemplate(page.getUrlTemplate())
                                .setStatus("ACTIVE"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                EmbeddedPageResponse.class);
        performGetWithParams("/api/v1/embedded-pages/objects/CUSTOMER/42",
                Map.of("platform", "WEB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].url")
                        .value("https://example.invalid/accounts/42"));
        performAs(OWNER_ACTOR_ID, TENANT_ID, delete("/api/v1/embedded-pages/{pageId}", page.getId())
                .param("version", active.getVersion().toString()))
                .andExpect(status().is(422));
    }

    @Test
    void fieldLifecycleRejectsDependenciesAndReadsFailClosedForStaleMetadata() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customization Owner");
        FieldDefinitionResponse field = createTextField("Lifecycle");
        performPut("/api/v1/fields/objects/CUSTOMER/layout",
                new FieldLayoutUpdateRequest().setOrderedCodes(List.of(field.getCode()))
                        .setColumnWidths(Map.of(field.getCode(), 180)))
                .andExpect(status().isOk());
        performPost("/api/v1/saved-views", new SavedViewCreateRequest()
                .setObjectType("CUSTOMER").setName("Lifecycle view")
                .setConditions(List.of(new FilterConditionRequest()
                        .setFieldCode(field.getCode()).setOperator("EQ")
                        .setValues(List.of("active")))))
                .andExpect(status().isOk());

        performAs(OWNER_ACTOR_ID, TENANT_ID,
                delete("/api/v1/fields/objects/CUSTOMER/{fieldId}", field.getId())
                        .param("version", field.getVersion().toString()))
                .andExpect(status().is(422));
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/fields/objects/CUSTOMER/{fieldId}", field.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new FieldUpdateRequest().setVersion(field.getVersion())
                                .setDisplayName(field.getDisplayName()).setSearchable(false))))
                .andExpect(status().is(422));

        new JdbcTemplate(dataSource).update(
                "UPDATE custom_field_definition SET status = 'INACTIVE', is_searchable = 0 "
                        + "WHERE tenant_id = ? AND id = ?", TENANT_ID, field.getId());
        FieldLayoutResponse layout = fromJson(performGetWithParams(
                "/api/v1/fields/objects/CUSTOMER/layout", Map.of())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldLayoutResponse.class);
        assertTrue(layout.getOrderedCodes().isEmpty());
        assertTrue(layout.getColumnWidths().isEmpty());
        performGetWithParams("/api/v1/saved-views", Map.of("objectType", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void objectGuardSerializesLifecycleAgainstLayoutAndSavedViewWrites() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customization Owner");
        FieldDefinitionResponse layoutField = createTextField("Concurrent layout");
        assertLifecycleWins(layoutField, () -> performPut(
                "/api/v1/fields/objects/CUSTOMER/layout",
                new FieldLayoutUpdateRequest().setOrderedCodes(List.of(layoutField.getCode())))
                .andReturn().getResponse().getStatus());
        assertEquals(0, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM field_layout_preference WHERE tenant_id = ?",
                Integer.class, TENANT_ID));

        FieldDefinitionResponse viewField = createTextField("Concurrent view");
        assertLifecycleWins(viewField, () -> performPost("/api/v1/saved-views",
                new SavedViewCreateRequest().setObjectType("CUSTOMER").setName("Concurrent")
                        .setConditions(List.of(new FilterConditionRequest()
                                .setFieldCode(viewField.getCode()).setOperator("EQ")
                                .setValues(List.of("value")))))
                .andReturn().getResponse().getStatus());
        assertEquals(0, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM saved_view WHERE tenant_id = ?", Integer.class, TENANT_ID));
    }

    @Test
    void objectGuardUpsertSerializesExistingAndMissingRowsWithoutDeadlock() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customization Owner");
        TenantId tenantId = new TenantId(TENANT_ID);
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> objectLock.acquire(tenantId, BusinessObjectType.CUSTOMER));
        assertGuardRowCount(BusinessObjectType.CUSTOMER, 1);
        assertConcurrentObjectLock(BusinessObjectType.CUSTOMER);

        new JdbcTemplate(dataSource).update(
                "DELETE FROM customization_object_guard WHERE tenant_id = ? AND object_type = ?",
                TENANT_ID, BusinessObjectType.OPPORTUNITY.name());
        assertGuardRowCount(BusinessObjectType.OPPORTUNITY, 0);
        assertConcurrentObjectLock(BusinessObjectType.OPPORTUNITY);
    }

    private FieldDefinitionResponse createTextField(String name) throws Exception {
        return fromJson(performPost("/api/v1/fields/objects/CUSTOMER",
                new FieldCreateRequest().setDisplayName(name).setFieldType("TEXT"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
    }

    private TenantRoleResponse createRole(String name, String... authorities) throws Exception {
        return fromJson(performPost("/api/v1/authorization/roles",
                new RoleCreateRequest().setName(name).setGrants(java.util.Arrays.stream(authorities)
                        .map(authority -> new RoleGrantRequest().setAuthorityCode(authority))
                        .toList()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                TenantRoleResponse.class);
    }

    private void assertLifecycleWins(
            FieldDefinitionResponse response, Callable<Integer> dependentWrite) throws Exception {
        TenantId tenantId = new TenantId(TENANT_ID);
        FieldDefinitionId fieldId = new FieldDefinitionId(response.getId());
        CountDownLatch lifecycleChecked = new CountDownLatch(1);
        CountDownLatch releaseLifecycle = new CountDownLatch(1);
        CountDownLatch dependentStarted = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> lifecycle = executor.submit(() -> transaction.executeWithoutResult(status -> {
                objectLock.acquire(tenantId, BusinessObjectType.CUSTOMER);
                FieldDefinition field = fieldRepository.findById(
                        tenantId, BusinessObjectType.CUSTOMER, fieldId).orElseThrow();
                assertFalse(layoutRepository.existsReferencingField(
                        tenantId, BusinessObjectType.CUSTOMER, field.getCode()));
                assertFalse(viewRepository.existsReferencingField(
                        tenantId, BusinessObjectType.CUSTOMER, field.getCode()));
                lifecycleChecked.countDown();
                await(releaseLifecycle);
                field.deactivate();
                fieldRepository.save(tenantId, field);
            }));
            assertTrue(lifecycleChecked.await(5, TimeUnit.SECONDS));

            Future<Integer> dependent = executor.submit(() -> {
                dependentStarted.countDown();
                return dependentWrite.call();
            });
            assertTrue(dependentStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,
                    () -> dependent.get(250, TimeUnit.MILLISECONDS),
                    "Dependent write must wait for the customization object lock");

            releaseLifecycle.countDown();
            lifecycle.get(5, TimeUnit.SECONDS);
            assertEquals(422, dependent.get(5, TimeUnit.SECONDS));
        } finally {
            releaseLifecycle.countDown();
            executor.shutdownNow();
        }
    }

    private void assertConcurrentObjectLock(BusinessObjectType objectType) throws Exception {
        int contenderCount = 6;
        CountDownLatch ready = new CountDownLatch(contenderCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger currentHolders = new AtomicInteger();
        AtomicInteger maximumHolders = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(contenderCount);
        List<Future<?>> contenders = new ArrayList<>(contenderCount);
        try {
            for (int index = 0; index < contenderCount; index++) {
                contenders.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        objectLock.acquire(new TenantId(TENANT_ID), objectType);
                        int holders = currentHolders.incrementAndGet();
                        maximumHolders.accumulateAndGet(holders, Math::max);
                        try {
                            pauseForContention();
                        } finally {
                            currentHolders.decrementAndGet();
                        }
                    });
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> contender : contenders) {
                contender.get(10, TimeUnit.SECONDS);
            }
            assertEquals(1, maximumHolders.get());
            assertEquals(0, currentHolders.get());
            assertGuardRowCount(objectType, 1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void assertGuardRowCount(BusinessObjectType objectType, int expected) {
        assertEquals(expected, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM customization_object_guard "
                        + "WHERE tenant_id = ? AND object_type = ?",
                Integer.class, TENANT_ID, objectType.name()));
    }

    private static void pauseForContention() {
        try {
            TimeUnit.MILLISECONDS.sleep(40);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(
                    "Interrupted while holding the customization object lock", exception);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for the concurrent customization write");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for a concurrent operation", exception);
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
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')",
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
        clearCustomizationData();
    }

    @Override
    protected void clearTestData() {
        clearCustomizationData();
    }

    private void clearCustomizationData() {
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
