package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.ActivityCreateRequest;
import io.github.archetom.crm.api.dto.request.CustomerAddressRequest;
import io.github.archetom.crm.api.dto.request.CustomerBatchTagRequest;
import io.github.archetom.crm.api.dto.request.CustomerCollaboratorRequest;
import io.github.archetom.crm.api.dto.request.CustomerCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerCsvExportRequest;
import io.github.archetom.crm.api.dto.request.CustomerCreateRequest;
import io.github.archetom.crm.api.dto.request.CustomerDuplicateCheckRequest;
import io.github.archetom.crm.api.dto.request.CustomerParentUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerQueryRequest;
import io.github.archetom.crm.api.dto.request.CustomerStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerStateUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerTagsUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerVersionRequest;
import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldUpdateRequest;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.RoleAssignmentCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldAccessRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldPolicyReplaceRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantRequest;
import io.github.archetom.crm.api.dto.request.SavedViewCreateRequest;
import io.github.archetom.crm.api.dto.response.CustomerResponse;
import io.github.archetom.crm.api.dto.response.FieldDefinitionResponse;
import io.github.archetom.crm.api.dto.response.SavedViewResponse;
import io.github.archetom.crm.api.dto.response.TenantRoleResponse;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerExportQuery;
import io.github.archetom.crm.domain.model.CustomerExportRow;
import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end customer boundary coverage against the production MySQL mappings. */
@RequiresDatabase
class CustomerIntegrationTest extends BaseIntegrationTest {

    private static final long OWNER_ACTOR_ID = 888888L;
    private static final long TENANT_ID = 999999L;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private QueryServiceTemplate queryTemplate;

    @Test
    void customerListReturnsLatestDirectActivityOnlyToActivityReaders() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customer activity summary owner");
        CustomerResponse customer = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Activity summary customer"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        LocalDateTime latestTime = LocalDateTime.of(2026, 8, 11, 9, 45);
        performPost("/api/v1/activities", new ActivityCreateRequest().setSubjectType("CUSTOMER")
                        .setSubjectId(customer.getId()).setCategory("CALL")
                        .setContent("Earlier follow-up")
                        .setOccurredTime(latestTime.minusHours(1)))
                .andExpect(status().isOk());
        performPost("/api/v1/activities", new ActivityCreateRequest().setSubjectType("CUSTOMER")
                        .setSubjectId(customer.getId()).setCategory("CALL")
                        .setContent("Latest follow-up")
                        .setOccurredTime(latestTime))
                .andExpect(status().isOk());

        performGet("/api/v1/customers")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].lastActivityTime")
                        .value("2026-08-11T09:45:00"))
                .andExpect(jsonPath("$.objectList[0].lastActivityContent").value("Latest follow-up"));

        long customerOnlyReader = 777701L;
        seedActiveMember(customerOnlyReader, "Customer summary reader");
        TenantRoleResponse customerReader = createRole("Customer summary reader",
                new RoleGrantRequest().setAuthorityCode("customers:read").setDataScope("TENANT"));
        setFieldPolicy(customerReader.getId(), csvNamePolicy());
        assignRole(customerReader.getId(), customerOnlyReader);
        performAs(customerOnlyReader, TENANT_ID, get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].lastActivityTime").doesNotExist())
                .andExpect(jsonPath("$.objectList[0].lastActivityContent").doesNotExist());

        long activityReader = 777702L;
        seedActiveMember(activityReader, "Activity summary reader");
        TenantRoleResponse activityRole = createRole("Activity summary reader",
                new RoleGrantRequest().setAuthorityCode("customers:read").setDataScope("TENANT"),
                new RoleGrantRequest().setAuthorityCode("activities:read"));
        setFieldPolicy(activityRole.getId(), csvNamePolicy());
        assignRole(activityRole.getId(), activityReader);
        performAs(activityReader, TENANT_ID, get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].lastActivityTime")
                        .value("2026-08-11T09:45:00"))
                .andExpect(jsonPath("$.objectList[0].lastActivityContent").value("Latest follow-up"));
    }

    @Test
    void customerCollaboratorReadReturnsAnAuthoritativeVersionedBaseline() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customer collaborator baseline owner");
        long collaboratorActor = 777703L;
        seedActiveMember(collaboratorActor, "Customer collaborator baseline member");
        CustomerResponse customer = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Customer collaborator baseline"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        CustomerResponse updated = replaceCollaborators(customer, collaboratorActor, "READ");

        performAs(OWNER_ACTOR_ID, TENANT_ID,
                get("/api/v1/customers/{customerId}/collaborators", customer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(updated.getVersion()))
                .andExpect(jsonPath("$.callerState").value("OWNER"))
                .andExpect(jsonPath("$.collaborators[0].actorId").value(collaboratorActor))
                .andExpect(jsonPath("$.collaborators[0].access").value("READ"));
    }

    @Test
    void customerCustomNumericFieldSortUsesCanonicalNumericValues() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customer numeric sort owner");
        FieldDefinitionResponse score = fromJson(performPost("/api/v1/fields/objects/CUSTOMER",
                new FieldCreateRequest().setDisplayName("Customer score").setFieldType("INTEGER"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        CustomerResponse two = createCustomer(OWNER_ACTOR_ID, TENANT_ID, "Two score", OWNER_ACTOR_ID,
                score.getCode(), "2", false);
        CustomerResponse ten = createCustomer(OWNER_ACTOR_ID, TENANT_ID, "Ten score", OWNER_ACTOR_ID,
                score.getCode(), "10", false);

        mockMvc.perform(withTestActor(get("/api/v1/customers")
                        .param("customSortFieldCode", score.getCode())
                        .param("sortDirection", "ASC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(two.getId()))
                .andExpect(jsonPath("$.objectList[1].id").value(ten.getId()));
    }

    @Test
    void customerLifecycleUsesCustomFieldsSavedViewsNearbyAndOptimisticLocking() throws Exception {
        mockMvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customer Owner");
        performGetWithParams("/api/v1/customers/nearby", Map.of(
                        "longitude", "121.4737", "latitude", "31.2304", "radiusMeters", "0"))
                .andExpect(status().isBadRequest());
        assertEquals(15, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM custom_field_definition "
                        + "WHERE tenant_id = ? AND object_type = 'CUSTOMER' AND is_builtin = 1",
                Integer.class, TENANT_ID));

        FieldDefinitionResponse segment = createTextField("Segment");
        CustomerResponse customer = createCustomer(
                OWNER_ACTOR_ID, TENANT_ID, "Acme Shanghai", OWNER_ACTOR_ID,
                segment.getCode(), "enterprise", true);
        assertEquals(List.of("Enterprise"), customer.getTags());
        assertEquals(List.of("enterprise"), customer.getCustomFields().get(segment.getCode()));
        assertEquals("hq", customer.getAddresses().getFirst().getKey());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO crm_customer_address "
                        + "(tenant_id, customer_id, address_key, address_line, longitude, latitude, is_primary) "
                        + "VALUES (?, ?, 'longitude-only', 'Invalid', 121.0000000, NULL, 0)",
                TENANT_ID, customer.getId()));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO crm_customer_address "
                        + "(tenant_id, customer_id, address_key, address_line, longitude, latitude, is_primary) "
                        + "VALUES (?, ?, 'latitude-only', 'Invalid', NULL, 31.0000000, 0)",
                TENANT_ID, customer.getId()));

        SavedViewResponse view = fromJson(performPost("/api/v1/saved-views",
                new SavedViewCreateRequest().setObjectType("CUSTOMER").setName("Enterprise")
                        .setConditions(List.of(new FilterConditionRequest()
                                .setFieldCode(segment.getCode()).setOperator("EQ")
                                .setValues(List.of("enterprise")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SavedViewResponse.class);
        mockMvc.perform(withTestActor(get("/api/v1/customers")
                        .param("savedViewId", view.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(customer.getId()));
        performAs(OWNER_ACTOR_ID, TENANT_ID, post("/api/v1/customers/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CustomerQueryRequest().setConditions(List.of(
                                new FilterConditionRequest().setFieldCode(segment.getCode())
                                        .setOperator("EQ").setValues(List.of("enterprise")))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(customer.getId()));

        CustomerResponse alphabeticCustomer = createCustomer(
                OWNER_ACTOR_ID, TENANT_ID, "Aardvark Beijing", OWNER_ACTOR_ID,
                segment.getCode(), "consumer", false);
        mockMvc.perform(withTestActor(get("/api/v1/customers")
                        .param("sortField", "NAME").param("sortDirection", "ASC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(alphabeticCustomer.getId()))
                .andExpect(jsonPath("$.objectList[1].id").value(customer.getId()));
        mockMvc.perform(withTestActor(get("/api/v1/customers")
                        .param("customSortFieldCode", segment.getCode())
                        .param("sortDirection", "ASC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(alphabeticCustomer.getId()))
                .andExpect(jsonPath("$.objectList[1].id").value(customer.getId()));
        mockMvc.perform(withTestActor(get("/api/v1/customers")
                        .param("sortField", "NAME")
                        .param("customSortFieldCode", segment.getCode())
                        .param("sortDirection", "ASC")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withTestActor(get("/api/v1/customers")
                        .param("customSortFieldCode", CustomerFieldCatalog.NAME)
                        .param("sortDirection", "ASC")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withTestActor(get("/api/v1/customers")
                        .param("sortField", "DEAL_TIME").param("sortDirection", "ASC")))
                .andExpect(status().isBadRequest());

        performPut("/api/v1/customers/" + customer.getId() + "/star",
                new CustomerStarUpdateRequest().setStarred(true))
                .andExpect(status().isOk());
        mockMvc.perform(withTestActor(get("/api/v1/customers").param("starred", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].starred").value(true));
        performPost("/api/v1/customers/duplicate-check",
                new CustomerDuplicateCheckRequest().setName("Acme Shanghai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
        performGetWithParams("/api/v1/customers/nearby", Map.of(
                        "longitude", "121.4737", "latitude", "31.2304",
                        "radiusMeters", "1000", "page", "1", "size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].customer.id").value(customer.getId()));

        CustomerResponse updated = fromJson(performPut("/api/v1/customers/" + customer.getId(),
                new CustomerUpdateRequest().setVersion(customer.getVersion())
                        .setName("Acme Updated"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        assertTrue(updated.getVersion() > customer.getVersion());
        assertEquals("+8613800138000", updated.getPhoneNumber());
        CustomerResponse cleared = fromJson(performPut("/api/v1/customers/" + customer.getId(),
                new CustomerUpdateRequest().setVersion(updated.getVersion())
                        .setPhoneNumber(""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        assertNull(cleared.getPhoneNumber());
        performPut("/api/v1/customers/" + customer.getId(),
                new CustomerUpdateRequest().setVersion(customer.getVersion())
                        .setName("Stale update"))
                .andExpect(status().isConflict());

        long otherActorId = 666666L;
        long otherTenantId = 666001L;
        bootstrap(otherActorId, otherTenantId, "Other Customer Owner");
        performAs(otherActorId, otherTenantId, get("/api/v1/customers/{customerId}", customer.getId()))
                .andExpect(status().isNotFound());
        performAs(otherActorId, otherTenantId,
                put("/api/v1/customers/{customerId}", customer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CustomerUpdateRequest()
                                .setVersion(cleared.getVersion())
                                .setName("Cross-tenant update"))))
                .andExpect(status().isNotFound());

        performAs(OWNER_ACTOR_ID, TENANT_ID,
                delete("/api/v1/customers/{customerId}", customer.getId())
                        .param("version", cleared.getVersion().toString()))
                .andExpect(status().isOk());
        performGet("/api/v1/customers/{customerId}", customer.getId())
                .andExpect(status().isNotFound());
    }

    @Test
    void customerCsvExportUsesTheAuthorizedMySqlProjection() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customer CSV Export Owner");
        FieldDefinitionResponse segment = createTextField("Export segment");
        CustomerResponse customer = createCustomer(
                OWNER_ACTOR_ID, TENANT_ID, "Export account", OWNER_ACTOR_ID,
                segment.getCode(), "enterprise", false);

        performAs(OWNER_ACTOR_ID, TENANT_ID, post("/api/v1/customers/export/csv")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CustomerCsvExportRequest().setFieldCodes(List.of(
                                "customer_name", "locked", segment.getCode())))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"customers.csv\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string("\uFEFFcustomer_id,customer_name,locked," + segment.getCode() + "\r\n"
                                + customer.getId() + ",Export account," + customer.getLocked()
                                + ",enterprise\r\n"));

        assertEquals(1, new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM crm_customer_field_value "
                        + "WHERE tenant_id = ? AND customer_id = ?",
                Integer.class, TENANT_ID, customer.getId()));
    }

    @Test
    void customerCsvExportIntersectsIndependentReadAndOwnerExportScopes() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customer CSV scope owner");
        long tenantReadSelfExportActor = 7_701_001L;
        long selfReadTenantExportActor = 7_701_002L;
        long readOnlyActor = 7_701_003L;
        long exportOnlyActor = 7_701_004L;
        seedActiveMember(tenantReadSelfExportActor, "Tenant read self export");
        seedActiveMember(selfReadTenantExportActor, "Self read tenant export");
        seedActiveMember(readOnlyActor, "Read only");
        seedActiveMember(exportOnlyActor, "Export only");

        TenantRoleResponse tenantReadSelfExport = createRole("CSV tenant read self export",
                new RoleGrantRequest().setAuthorityCode("customers:read").setDataScope("TENANT"),
                new RoleGrantRequest().setAuthorityCode("customers:export").setDataScope("SELF"));
        TenantRoleResponse selfReadTenantExport = createRole("CSV self read tenant export",
                new RoleGrantRequest().setAuthorityCode("customers:read").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("customers:export").setDataScope("TENANT"));
        TenantRoleResponse readOnly = createRole("CSV read only",
                new RoleGrantRequest().setAuthorityCode("customers:read").setDataScope("TENANT"));
        TenantRoleResponse exportOnly = createRole("CSV export only",
                new RoleGrantRequest().setAuthorityCode("customers:export").setDataScope("TENANT"));
        setFieldPolicy(tenantReadSelfExport.getId(), csvNamePolicy());
        setFieldPolicy(selfReadTenantExport.getId(), csvNamePolicy());
        assignRole(tenantReadSelfExport.getId(), tenantReadSelfExportActor);
        assignRole(selfReadTenantExport.getId(), selfReadTenantExportActor);
        assignRole(readOnly.getId(), readOnlyActor);
        assignRole(exportOnly.getId(), exportOnlyActor);

        String code = createTextField("CSV scope marker").getCode();
        CustomerResponse selfExported = createCustomer(OWNER_ACTOR_ID, TENANT_ID,
                "Self export included", tenantReadSelfExportActor, code, "a", false);
        CustomerResponse selfCollaboratorOnly = createCustomer(OWNER_ACTOR_ID, TENANT_ID,
                "Self export collaborator only", OWNER_ACTOR_ID, code, "b", false);
        CustomerResponse pooled = createCustomer(OWNER_ACTOR_ID, TENANT_ID,
                "Self export pool", tenantReadSelfExportActor, code, "c", false);
        CustomerResponse deleted = createCustomer(OWNER_ACTOR_ID, TENANT_ID,
                "Self export deleted", tenantReadSelfExportActor, code, "d", false);
        CustomerResponse tenantExportOwned = createCustomer(OWNER_ACTOR_ID, TENANT_ID,
                "Tenant export owned", selfReadTenantExportActor, code, "e", false);
        CustomerResponse tenantExportCollaborator = createCustomer(OWNER_ACTOR_ID, TENANT_ID,
                "Tenant export collaborator", OWNER_ACTOR_ID, code, "f", false);
        CustomerResponse tenantExportHidden = createCustomer(OWNER_ACTOR_ID, TENANT_ID,
                "Tenant export hidden", OWNER_ACTOR_ID, code, "g", false);
        replaceCollaborators(selfCollaboratorOnly, tenantReadSelfExportActor, "READ");
        replaceCollaborators(tenantExportCollaborator, selfReadTenantExportActor, "READ");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("UPDATE crm_customer SET status = 'IN_POOL' WHERE tenant_id = ? AND id = ?",
                TENANT_ID, pooled.getId());
        jdbc.update("UPDATE crm_customer SET status = 'DELETED' WHERE tenant_id = ? AND id = ?",
                TENANT_ID, deleted.getId());

        assertCsvCustomerIds(tenantReadSelfExportActor, TENANT_ID, List.of(selfExported.getId()));
        assertCsvCustomerIds(selfReadTenantExportActor, TENANT_ID,
                List.of(tenantExportOwned.getId(), tenantExportCollaborator.getId()));

        performAs(readOnlyActor, TENANT_ID, csvExportRequest())
                .andExpect(status().isForbidden());
        performAs(exportOnlyActor, TENANT_ID, csvExportRequest())
                .andExpect(status().isForbidden());

        long otherActor = 7_701_005L;
        long otherTenant = 7_701_099L;
        bootstrap(otherActor, otherTenant, "Other tenant CSV owner");
        String crossTenant = performAs(otherActor, otherTenant, csvExportRequest())
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertEquals("\uFEFFcustomer_id,customer_name\r\n", crossTenant);
    }

    @Test
    void customerCsvExportIntersectsDepartmentAndSubtreeScopesAndRejectsUnavailableScopes()
            throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customer CSV department scope owner");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long sales = insertDepartment(jdbc, 0L, "CSV Sales");
        long salesEast = insertDepartment(jdbc, sales, "CSV Sales East");
        long service = insertDepartment(jdbc, 0L, "CSV Service");
        long subtreeReadDepartmentExportActor = 7_702_001L;
        long departmentReadSubtreeExportActor = 7_702_002L;
        long salesOwner = 7_702_003L;
        long eastOwner = 7_702_004L;
        long serviceOwner = 7_702_005L;
        long inactiveSalesOwner = 7_702_006L;
        long noDepartmentActor = 7_702_007L;
        seedActiveMember(subtreeReadDepartmentExportActor, "Subtree read department export", sales);
        seedActiveMember(departmentReadSubtreeExportActor, "Department read subtree export", sales);
        seedActiveMember(salesOwner, "CSV Sales owner", sales);
        seedActiveMember(eastOwner, "CSV Sales East owner", salesEast);
        seedActiveMember(serviceOwner, "CSV Service owner", service);
        seedActiveMember(inactiveSalesOwner, "Inactive CSV Sales owner", sales);
        seedActiveMember(noDepartmentActor, "No CSV department");

        TenantRoleResponse subtreeReadDepartmentExport = createRole(
                "CSV subtree read department export",
                new RoleGrantRequest().setAuthorityCode("customers:read")
                        .setDataScope("DEPARTMENT_SUBTREE"),
                new RoleGrantRequest().setAuthorityCode("customers:export")
                        .setDataScope("DEPARTMENT"));
        TenantRoleResponse departmentReadSubtreeExport = createRole(
                "CSV department read subtree export",
                new RoleGrantRequest().setAuthorityCode("customers:read")
                        .setDataScope("DEPARTMENT"),
                new RoleGrantRequest().setAuthorityCode("customers:export")
                        .setDataScope("DEPARTMENT_SUBTREE"));
        TenantRoleResponse unavailableDepartmentScope = createRole("CSV unavailable department scope",
                new RoleGrantRequest().setAuthorityCode("customers:read")
                        .setDataScope("DEPARTMENT"),
                new RoleGrantRequest().setAuthorityCode("customers:export")
                        .setDataScope("DEPARTMENT"));
        setFieldPolicy(subtreeReadDepartmentExport.getId(), csvNamePolicy());
        setFieldPolicy(departmentReadSubtreeExport.getId(), csvNamePolicy());
        setFieldPolicy(unavailableDepartmentScope.getId(), csvNamePolicy());
        assignRole(subtreeReadDepartmentExport.getId(), subtreeReadDepartmentExportActor);
        assignRole(departmentReadSubtreeExport.getId(), departmentReadSubtreeExportActor);
        assignRole(unavailableDepartmentScope.getId(), noDepartmentActor);

        String code = createTextField("CSV department scope marker").getCode();
        CustomerResponse salesCustomer = createCustomer(OWNER_ACTOR_ID, TENANT_ID,
                "CSV sales included", salesOwner, code, "sales", false);
        createCustomer(OWNER_ACTOR_ID, TENANT_ID, "CSV east excluded by one predicate", eastOwner,
                code, "east", false);
        createCustomer(OWNER_ACTOR_ID, TENANT_ID, "CSV service excluded", serviceOwner,
                code, "service", false);
        CustomerResponse inactiveOwnerCustomer = createCustomer(OWNER_ACTOR_ID, TENANT_ID,
                "CSV inactive owner excluded", inactiveSalesOwner, code, "inactive", false);
        jdbc.update("UPDATE organization_membership SET status = 'SUSPENDED' "
                        + "WHERE tenant_id = ? AND actor_id = ?",
                TENANT_ID, inactiveSalesOwner);

        assertCsvCustomerIds(subtreeReadDepartmentExportActor, TENANT_ID,
                List.of(salesCustomer.getId()));
        assertCsvCustomerIds(departmentReadSubtreeExportActor, TENANT_ID,
                List.of(salesCustomer.getId()));
        assertEquals("\uFEFFcustomer_id,customer_name\r\n",
                csvContent(noDepartmentActor, TENANT_ID));
        assertTrue(!csvContent(subtreeReadDepartmentExportActor, TENANT_ID)
                .contains(inactiveOwnerCustomer.getId().toString()));
    }

    @Test
    void customerCsvExportAppliesAllFiltersBeforeTheFiveThousandAndOneRowProbe() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customer CSV filter owner");
        FieldDefinitionResponse segment = createTextField("CSV export filter segment");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Object[]> nonMatches = new ArrayList<>();
        for (int index = 0; index < 5_001; index++) {
            nonMatches.add(new Object[]{TENANT_ID, OWNER_ACTOR_ID, "CSV noise " + index});
        }
        jdbc.batchUpdate("INSERT INTO crm_customer (tenant_id, owner_actor_id, name, status) "
                        + "VALUES (?, ?, ?, 'ACTIVE')", nonMatches);
        CustomerResponse matching = createCustomer(OWNER_ACTOR_ID, TENANT_ID,
                "CSV filter match", OWNER_ACTOR_ID, segment.getCode(), "export-match", false);
        performPut("/api/v1/customers/" + matching.getId() + "/star",
                new CustomerStarUpdateRequest().setStarred(true)).andExpect(status().isOk());
        SavedViewResponse view = fromJson(performPost("/api/v1/saved-views",
                new SavedViewCreateRequest().setObjectType("CUSTOMER").setName("CSV export filtered")
                        .setConditions(List.of(new FilterConditionRequest()
                                .setFieldCode(segment.getCode()).setOperator("EQ")
                                .setValues(List.of("export-match")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SavedViewResponse.class);

        CustomerCsvExportRequest request = new CustomerCsvExportRequest()
                .setFieldCodes(List.of(CustomerFieldCatalog.NAME))
                .setKeyword("CSV filter")
                .setName("CSV filter match")
                .setOwnerActorId(OWNER_ACTOR_ID)
                .setStarred(true)
                .setSavedViewId(view.getId());
        String content = performAs(OWNER_ACTOR_ID, TENANT_ID,
                post("/api/v1/customers/export/csv").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertEquals("\uFEFFcustomer_id,customer_name\r\n" + matching.getId()
                + ",CSV filter match\r\n", content);

        Map<String, Object> plan = jdbc.queryForMap("EXPLAIN SELECT id FROM crm_customer "
                        + "WHERE tenant_id = ? AND status = 'ACTIVE' AND owner_actor_id = ? "
                        + "AND name LIKE CONCAT(?, '%') ESCAPE '!' ORDER BY id ASC LIMIT 5001",
                TENANT_ID, OWNER_ACTOR_ID, "CSV filter match");
        String explain = plan.values().stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(explain.contains("Index range scan on crm_customer using idx_crm_customer_tenant_name"),
                () -> explain);
        assertTrue(!explain.contains("Table scan on crm_customer"), () -> explain);
    }

    @Test
    void customerCsvExportKeepsOneMySqlRepeatableReadSnapshotAcrossProjectionBatches() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customer CSV snapshot owner");
        String code = createTextField("CSV snapshot marker").getCode();
        CustomerResponse customer = createCustomer(OWNER_ACTOR_ID, TENANT_ID,
                "Snapshot before", OWNER_ACTOR_ID, code, "snapshot", false);
        CustomerVisibility readVisibility = new CustomerVisibility(DataScope.TENANT,
                new MemberId(OWNER_ACTOR_ID), Set.of(), CustomerAccessMode.READ);
        CustomerVisibility exportVisibility = new CustomerVisibility(DataScope.TENANT,
                new MemberId(OWNER_ACTOR_ID), Set.of(), CustomerAccessMode.OWNER_SCOPE);
        CustomerExportQuery query = new CustomerExportQuery(readVisibility, exportVisibility,
                null, Map.of(), null, null, null, null, List.of());
        CountDownLatch idsSelected = new CountDownLatch(1);
        CountDownLatch mutationCommitted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> export = executor.submit(() -> {
                var result = queryTemplate.execute(UseCaseOperation.CUSTOMER_CSV_EXPORT,
                        new ServiceOperation<List<CustomerExportRow>>() {
                            @Override
                            public List<CustomerExportRow> execute() {
                                List<CustomerId> ids = customerRepository.findExportCustomerIds(
                                        new TenantId(TENANT_ID), query, 5_001);
                                idsSelected.countDown();
                                await(mutationCommitted, "CSV mutation was not committed");
                                return customerRepository.findExportRows(new TenantId(TENANT_ID), ids,
                                        List.of(new io.github.archetom.crm.domain.valueobject.FieldCode(
                                                CustomerFieldCatalog.NAME)));
                            }
                        });
                assertTrue(result.isSuccess());
                return result.getData().getFirst().cells().getFirst().scalarValue();
            });
            await(idsSelected, "CSV export did not select its IDs");
            new JdbcTemplate(dataSource).update("UPDATE crm_customer SET name = ? WHERE tenant_id = ? AND id = ?",
                    "Snapshot after", TENANT_ID, customer.getId());
            mutationCommitted.countDown();
            assertEquals("Snapshot before", export.get(10, TimeUnit.SECONDS));
        } finally {
            mutationCommitted.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void normalizedPersistenceRepresentationsRespectDatabaseContracts() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customer Normalization Owner");
        FieldDefinitionResponse uniqueText = fromJson(performPost(
                "/api/v1/fields/objects/CUSTOMER",
                new FieldCreateRequest().setDisplayName("Normalized unique text")
                        .setFieldType("TEXT").setUnique(true))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        String compatibilityLigatures = "ﬃ".repeat(500);
        CustomerResponse customer = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Normalization account")
                        .setTags(List.of("e", "é", "VIP", "Ａ", "ﬃ"))
                        .setCustomFields(Map.of(uniqueText.getCode(),
                                List.of(compatibilityLigatures))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(List.of("e", "é", "VIP", "Ａ", "ﬃ"), customer.getTags());
        assertEquals(5, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_customer_tag "
                        + "WHERE tenant_id = ? AND customer_id = ?",
                Integer.class, TENANT_ID, customer.getId()));
        assertEquals(1_500, jdbc.queryForObject(
                "SELECT CHAR_LENGTH(normalized_value) "
                        + "FROM crm_customer_unique_field_value "
                        + "WHERE tenant_id = ? AND customer_id = ? AND field_id = ?",
                Integer.class, TENANT_ID, customer.getId(), uniqueText.getId()));

        CustomerResponse aliases = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Tag aliases")
                        .setTags(List.of("vip", "a", "ffi")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        String[] catalog = fromJson(performGet("/api/v1/customers/tags")
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                String[].class);
        assertEquals(1, java.util.Arrays.stream(catalog)
                .filter(tag -> tag.equals("VIP") || tag.equals("vip")).count());
        assertEquals(1, java.util.Arrays.stream(catalog)
                .filter(tag -> tag.equals("Ａ") || tag.equals("a")).count());
        assertEquals(1, java.util.Arrays.stream(catalog)
                .filter(tag -> tag.equals("ﬃ") || tag.equals("ffi")).count());

        CustomerResponse batchChanged = fromJson(performPost("/api/v1/customers/batch/tags",
                new CustomerBatchTagRequest()
                        .setCustomers(List.of(new CustomerVersionRequest()
                                .setCustomerId(customer.getId()).setVersion(customer.getVersion())))
                        .setRemovedTags(List.of("ffi")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse[].class)[0];
        assertTrue(!batchChanged.getTags().contains("ﬃ"));
        // Re-read: normalized removal on one record must not strip the alias tag off another.
        assertTrue(fromJson(performGet("/api/v1/customers/" + aliases.getId())
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class).getTags().contains("ffi"));

        String expandedWebsite = "https://example.invalid/" + "你".repeat(476);
        performPut("/api/v1/customers/" + customer.getId(),
                new CustomerUpdateRequest().setVersion(batchChanged.getVersion())
                        .setWebsite(expandedWebsite))
                .andExpect(status().is(422));
    }

    @Test
    void selfScopeAndFieldPoliciesPreventRecordAndDuplicateOracles() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customer Scope Owner");
        FieldDefinitionResponse segment = createTextField("Scope segment");
        long targetActorId = 777777L;
        seedActiveMember(targetActorId, "Scoped Member");
        performAs(targetActorId, TENANT_ID, get("/api/v1/customers"))
                .andExpect(status().isForbidden());

        TenantRoleResponse reader = createRole("Self Customer Readers",
                new RoleGrantRequest().setAuthorityCode("customers:read").setDataScope("SELF"));
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/fields/roles/{roleId}/objects/CUSTOMER/access", reader.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleFieldPolicyReplaceRequest().setAccesses(List.of(
                                new RoleFieldAccessRequest().setFieldCode("customer_name")
                                        .setAccessLevel("READ").setMaskType("NONE"),
                                new RoleFieldAccessRequest().setFieldCode(segment.getCode())
                                        .setAccessLevel("READ").setMaskType("PARTIAL"))))))
                .andExpect(status().isOk());
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                post("/api/v1/authorization/roles/{roleId}/assignments", reader.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleAssignmentCreateRequest()
                                .setActorIds(List.of(targetActorId)))))
                .andExpect(status().isOk());

        CustomerResponse owned = createCustomer(
                OWNER_ACTOR_ID, TENANT_ID, "Scoped Account", targetActorId,
                segment.getCode(), "enterprise", false);
        CustomerResponse privateCustomer = createCustomer(
                OWNER_ACTOR_ID, TENANT_ID, "Private Account", OWNER_ACTOR_ID,
                segment.getCode(), "private", false);

        performAs(targetActorId, TENANT_ID, get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].id").value(owned.getId()))
                .andExpect(jsonPath("$.objectList[0].name").value("Scoped Account"))
                .andExpect(jsonPath("$.objectList[0].customFields."
                        + segment.getCode() + "[0]").value("e********e"));
        performAs(targetActorId, TENANT_ID, get("/api/v1/customers")
                        .param("customSortFieldCode", segment.getCode())
                        .param("sortDirection", "ASC"))
                .andExpect(status().isForbidden());
        performAs(targetActorId, TENANT_ID,
                get("/api/v1/customers/{customerId}", privateCustomer.getId()))
                .andExpect(status().isNotFound());
        performAs(targetActorId, TENANT_ID, post("/api/v1/customers/duplicate-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CustomerDuplicateCheckRequest()
                                .setName("Private Account"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false));
        performAs(targetActorId, TENANT_ID, post("/api/v1/customers/duplicate-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CustomerDuplicateCheckRequest()
                                .setName("Scoped Account"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));

        long maskedActorId = 777778L;
        seedActiveMember(maskedActorId, "Masked Reader");
        TenantRoleResponse maskedReader = createRole("Masked Customer Readers",
                new RoleGrantRequest().setAuthorityCode("customers:read").setDataScope("SELF"));
        setFieldPolicy(maskedReader.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("customer_name")
                        .setAccessLevel("READ").setMaskType("PARTIAL"),
                new RoleFieldAccessRequest().setFieldCode("addresses")
                        .setAccessLevel("READ").setMaskType("PARTIAL"),
                new RoleFieldAccessRequest().setFieldCode("deal_status")
                        .setAccessLevel("READ").setMaskType("FULL")));
        assignRole(maskedReader.getId(), maskedActorId);
        CustomerResponse maskedCustomer = createCustomer(
                OWNER_ACTOR_ID, TENANT_ID, "Masked Account", maskedActorId,
                segment.getCode(), "masked", true);
        CustomerResponse wonCustomer = fromJson(performPut(
                "/api/v1/customers/" + maskedCustomer.getId() + "/state",
                new CustomerStateUpdateRequest().setVersion(maskedCustomer.getVersion())
                        .setLocked(false).setDealStatus("WON"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);

        CustomerResponse maskedResponse = fromJson(performAs(maskedActorId, TENANT_ID,
                get("/api/v1/customers/{customerId}", wonCustomer.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        assertEquals("M********t", maskedResponse.getName());
        assertEquals("[REDACTED]", maskedResponse.getDealStatus());
        org.junit.jupiter.api.Assertions.assertNull(maskedResponse.getDealTime());
        performAs(maskedActorId, TENANT_ID, get("/api/v1/customers")
                        .param("name", "Masked Account"))
                .andExpect(status().isForbidden());
        performAs(maskedActorId, TENANT_ID, get("/api/v1/customers")
                        .param("name", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1));
        performAs(maskedActorId, TENANT_ID, get("/api/v1/customers")
                        .param("ownerActorId", Long.toString(maskedActorId)))
                .andExpect(status().isForbidden());
        performAs(maskedActorId, TENANT_ID, get("/api/v1/customers/nearby")
                        .param("longitude", "121.4737").param("latitude", "31.2304")
                        .param("radiusMeters", "1000"))
                .andExpect(status().isForbidden());

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Object[]> hiddenDescendants = new ArrayList<>();
        for (int index = 0; index < 1_998; index++) {
            hiddenDescendants.add(new Object[]{
                    TENANT_ID, OWNER_ACTOR_ID, owned.getId(), "Hidden descendant " + index});
        }
        jdbc.batchUpdate("INSERT INTO crm_customer "
                        + "(tenant_id, owner_actor_id, parent_customer_id, name, status) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE')",
                hiddenDescendants);
        CustomerResponse visibleAfterHidden = fromJson(performPost(
                "/api/v1/customers", new CustomerCreateRequest()
                        .setName("Visible after hidden descendants")
                        .setOwnerActorId(targetActorId)
                        .setParentCustomerId(owned.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        performAs(targetActorId, TENANT_ID,
                get("/api/v1/customers/{customerId}/hierarchy", owned.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers.length()").value(2))
                .andExpect(jsonPath("$.customers[1].id").value(visibleAfterHidden.getId()));

        jdbc.batchUpdate("INSERT INTO crm_customer "
                        + "(tenant_id, owner_actor_id, parent_customer_id, name, status) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE')",
                List.of(
                        new Object[]{TENANT_ID, OWNER_ACTOR_ID, owned.getId(),
                                "Hidden descendant 1998"},
                        new Object[]{TENANT_ID, OWNER_ACTOR_ID, owned.getId(),
                                "Hidden descendant 1999"}));
        performPost("/api/v1/customers", new CustomerCreateRequest()
                .setName("Visible after hidden traversal budget")
                .setOwnerActorId(targetActorId)
                .setParentCustomerId(owned.getId()))
                .andExpect(status().is(422));

        CustomerResponse deepRoot = createCustomer(
                OWNER_ACTOR_ID, TENANT_ID, "Deep hierarchy root", targetActorId,
                segment.getCode(), "deep", false);
        long firstDeepId = jdbc.queryForObject(
                "SELECT COALESCE(MAX(id), 0) + 1 FROM crm_customer", Long.class);
        List<Object[]> deepDescendants = new ArrayList<>();
        for (int index = 0; index < 1_100; index++) {
            long id = firstDeepId + index;
            long parentId = index == 0 ? deepRoot.getId() : id - 1;
            deepDescendants.add(new Object[]{
                    id, TENANT_ID, OWNER_ACTOR_ID, parentId, "Deep descendant " + index});
        }
        jdbc.batchUpdate("INSERT INTO crm_customer "
                        + "(id, tenant_id, owner_actor_id, parent_customer_id, name, status) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                deepDescendants);
        performAs(targetActorId, TENANT_ID,
                get("/api/v1/customers/{customerId}/hierarchy", deepRoot.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers.length()").value(1))
                .andExpect(jsonPath("$.customers[0].id").value(deepRoot.getId()));
    }

    @Test
    void departmentScopesAndCollaboratorWriteAccessFailClosed() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customer Department Owner");
        FieldDefinitionResponse segment = createTextField("Department segment");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long sales = insertDepartment(jdbc, 0L, "Sales");
        long salesEast = insertDepartment(jdbc, sales, "Sales East");
        long service = insertDepartment(jdbc, 0L, "Service");
        long salesActor = 710001L;
        long subtreeActor = 710002L;
        long eastOwner = 710003L;
        long serviceOwner = 710004L;
        long collaboratorActor = 710005L;
        seedActiveMember(salesActor, "Sales Reader", sales);
        seedActiveMember(subtreeActor, "Sales Tree Reader", sales);
        seedActiveMember(eastOwner, "East Owner", salesEast);
        seedActiveMember(serviceOwner, "Service Owner", service);
        seedActiveMember(collaboratorActor, "Collaborator", service);

        TenantRoleResponse departmentReader = createRole("Department Customer Readers",
                new RoleGrantRequest().setAuthorityCode("customers:read")
                        .setDataScope("DEPARTMENT"));
        setFieldPolicy(departmentReader.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("customer_name")
                        .setAccessLevel("READ").setMaskType("NONE")));
        assignRole(departmentReader.getId(), salesActor);

        TenantRoleResponse subtreeReader = createRole("Subtree Customer Readers",
                new RoleGrantRequest().setAuthorityCode("customers:read")
                        .setDataScope("DEPARTMENT_SUBTREE"));
        setFieldPolicy(subtreeReader.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("customer_name")
                        .setAccessLevel("READ").setMaskType("NONE")));
        assignRole(subtreeReader.getId(), subtreeActor);

        createCustomer(OWNER_ACTOR_ID, TENANT_ID, "Sales Account", salesActor,
                segment.getCode(), "sales", false);
        CustomerResponse eastCustomer = createCustomer(OWNER_ACTOR_ID, TENANT_ID,
                "East Account", eastOwner,
                segment.getCode(), "east", false);
        createCustomer(OWNER_ACTOR_ID, TENANT_ID, "Service Account", serviceOwner,
                segment.getCode(), "service", false);

        performAs(salesActor, TENANT_ID, get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].name").value("Sales Account"));
        performAs(subtreeActor, TENANT_ID, get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(2));
        performAs(subtreeActor, TENANT_ID,
                delete("/api/v1/customers/{customerId}/collaborators/me", eastCustomer.getId())
                        .param("version", eastCustomer.getVersion().toString()))
                .andExpect(status().isNotFound());
        assertEquals(eastCustomer.getVersion(), jdbc.queryForObject(
                "SELECT version FROM crm_customer WHERE tenant_id = ? AND id = ?",
                Long.class, TENANT_ID, eastCustomer.getId()));

        TenantRoleResponse collaboratorRole = createRole("Customer Collaborators",
                new RoleGrantRequest().setAuthorityCode("customers:read").setDataScope("SELF"),
                new RoleGrantRequest().setAuthorityCode("customers:write").setDataScope("SELF"));
        setFieldPolicy(collaboratorRole.getId(), List.of(
                new RoleFieldAccessRequest().setFieldCode("customer_name")
                        .setAccessLevel("READ").setMaskType("NONE"),
                new RoleFieldAccessRequest().setFieldCode("tags")
                        .setAccessLevel("WRITE").setMaskType("NONE")));
        assignRole(collaboratorRole.getId(), collaboratorActor);
        CustomerResponse shared = createCustomer(OWNER_ACTOR_ID, TENANT_ID,
                "Shared Account", OWNER_ACTOR_ID, segment.getCode(), "shared", false);
        CustomerResponse readOnly = replaceCollaborators(shared, collaboratorActor, "READ");
        assertEquals(collaboratorActor, readOnly.getCollaborators().getFirst().getActorId());
        assertEquals("READ", readOnly.getCollaborators().getFirst().getAccess());
        performAs(collaboratorActor, TENANT_ID,
                put("/api/v1/customers/{customerId}/tags", shared.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CustomerTagsUpdateRequest()
                                .setVersion(readOnly.getVersion()).setTags(List.of("Blocked")))))
                .andExpect(status().isNotFound());
        CustomerResponse writable = replaceCollaborators(readOnly, collaboratorActor, "WRITE");
        performAs(collaboratorActor, TENANT_ID,
                put("/api/v1/customers/{customerId}/tags", shared.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CustomerTagsUpdateRequest()
                                .setVersion(writable.getVersion()).setTags(List.of("Shared")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0]").value("Shared"));
    }

    @Test
    void databaseArbitratesConcurrentUniqueValuesAndOpposingHierarchyMoves() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customer Concurrency Owner");
        FieldDefinitionResponse registration = fromJson(performPost(
                "/api/v1/fields/objects/CUSTOMER",
                new FieldCreateRequest().setDisplayName("Registration")
                        .setFieldType("TEXT").setUnique(true))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);

        List<Integer> createStatuses = runConcurrently(
                () -> customerCreateStatus("Concurrent A", registration.getCode(), "same-value"),
                () -> customerCreateStatus("Concurrent B", registration.getCode(), "same-value"));
        assertEquals(List.of(200, 409), createStatuses.stream().sorted().toList());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_customer WHERE tenant_id = ? AND status = 'ACTIVE'",
                Integer.class, TENANT_ID));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_customer_unique_field_value WHERE tenant_id = ?",
                Integer.class, TENANT_ID));
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/fields/objects/CUSTOMER/{fieldId}", registration.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new FieldUpdateRequest()
                                .setVersion(registration.getVersion())
                                .setDisplayName(registration.getDisplayName())
                                .setUnique(false))))
                .andExpect(status().is(422));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_customer_unique_field_value WHERE tenant_id = ?",
                Integer.class, TENANT_ID));

        FieldDefinitionResponse segment = createTextField("Hierarchy segment");
        CustomerResponse first = createCustomer(OWNER_ACTOR_ID, TENANT_ID, "Parent A",
                OWNER_ACTOR_ID, segment.getCode(), "a", false);
        CustomerResponse second = createCustomer(OWNER_ACTOR_ID, TENANT_ID, "Parent B",
                OWNER_ACTOR_ID, segment.getCode(), "b", false);
        CustomerResponse child = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Hierarchy child")
                        .setParentCustomerId(first.getId())
                        .setCustomFields(Map.of(segment.getCode(), List.of("child"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        assertEquals(first.getId(), child.getParentCustomerId());
        CustomerResponse movedChild = fromJson(performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/customers/{customerId}/parent", child.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CustomerParentUpdateRequest()
                                .setVersion(child.getVersion())
                                .setParentCustomerId(second.getId()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        assertEquals(second.getId(), movedChild.getParentCustomerId());
        List<Integer> hierarchyStatuses = runConcurrently(
                () -> parentUpdateStatus(first, second.getId()),
                () -> parentUpdateStatus(second, first.getId()));
        assertEquals(List.of(200, 422), hierarchyStatuses.stream().sorted().toList());
        int twoNodeCycles = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM crm_customer first_customer
                JOIN crm_customer second_customer
                  ON second_customer.tenant_id = first_customer.tenant_id
                 AND second_customer.id = first_customer.parent_customer_id
                WHERE first_customer.tenant_id = ?
                  AND second_customer.parent_customer_id = first_customer.id
                """, Integer.class, TENANT_ID);
        assertEquals(0, twoNodeCycles);
    }

    @Test
    void tagCatalogIncludesOlderVisibleCustomersBeyondTheFirstThousand() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Customer Tag Owner");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO crm_customer "
                        + "(tenant_id, owner_actor_id, name, status, updated_time) "
                        + "VALUES (?, ?, 'Legacy tagged account', 'ACTIVE', '2020-01-01 00:00:00')",
                TENANT_ID, OWNER_ACTOR_ID);
        Long legacyId = jdbc.queryForObject(
                "SELECT id FROM crm_customer WHERE tenant_id = ? AND name = 'Legacy tagged account'",
                Long.class, TENANT_ID);
        jdbc.update("INSERT INTO crm_customer_tag "
                        + "(tenant_id, customer_id, tag_name, tag_key) "
                        + "VALUES (?, ?, 'Legacy-only', 'legacy-only')",
                TENANT_ID, legacyId);
        List<Object[]> newerCustomers = new ArrayList<>();
        for (int index = 0; index < 1_000; index++) {
            newerCustomers.add(new Object[]{TENANT_ID, OWNER_ACTOR_ID, "Recent account " + index});
        }
        jdbc.batchUpdate("INSERT INTO crm_customer "
                        + "(tenant_id, owner_actor_id, name, status, updated_time) "
                        + "VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP(6))",
                newerCustomers);

        String[] tags = fromJson(performGet("/api/v1/customers/tags")
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                String[].class);
        assertTrue(List.of(tags).contains("Legacy-only"));
    }

    private FieldDefinitionResponse createTextField(String name) throws Exception {
        return fromJson(performPost("/api/v1/fields/objects/CUSTOMER",
                new FieldCreateRequest().setDisplayName(name).setFieldType("TEXT"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
    }

    private CustomerResponse createCustomer(
            long actorId, long tenantId, String name, long ownerActorId,
            String customFieldCode, String customFieldValue, boolean withAddress) throws Exception {
        CustomerCreateRequest request = new CustomerCreateRequest()
                .setName(name)
                .setPhoneNumber("+8613800138000")
                .setOwnerActorId(ownerActorId)
                .setTags(List.of("Enterprise"))
                .setCustomFields(Map.of(customFieldCode, List.of(customFieldValue)));
        if (withAddress) {
            request.setAddresses(List.of(new CustomerAddressRequest()
                    .setKey("hq").setLabel("Headquarters")
                    .setAddressLine("Example road 1")
                    .setLongitude(new BigDecimal("121.4737"))
                    .setLatitude(new BigDecimal("31.2304"))
                    .setPrimary(true)));
        }
        return fromJson(performAs(actorId, tenantId, post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
    }

    private TenantRoleResponse createRole(String name, RoleGrantRequest... grants) throws Exception {
        return fromJson(performPost("/api/v1/authorization/roles",
                new RoleCreateRequest().setName(name).setGrants(List.of(grants)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                TenantRoleResponse.class);
    }

    private List<RoleFieldAccessRequest> csvNamePolicy() {
        return List.of(new RoleFieldAccessRequest().setFieldCode(CustomerFieldCatalog.NAME)
                .setAccessLevel("READ").setMaskType("NONE"));
    }

    private MockHttpServletRequestBuilder csvExportRequest() {
        return post("/api/v1/customers/export/csv")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new CustomerCsvExportRequest()
                        .setFieldCodes(List.of(CustomerFieldCatalog.NAME))));
    }

    private void assertCsvCustomerIds(long actorId, long tenantId, List<Long> expectedIds) throws Exception {
        String content = csvContent(actorId, tenantId);
        List<Long> actualIds = content.lines().skip(1)
                .filter(line -> !line.isBlank())
                .map(line -> Long.parseLong(line.substring(0, line.indexOf(','))))
                .toList();
        assertEquals(expectedIds.stream().sorted().toList(), actualIds);
    }

    private String csvContent(long actorId, long tenantId) throws Exception {
        return performAs(actorId, tenantId, csvExportRequest())
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private static void await(CountDownLatch latch, String failureMessage) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError(failureMessage);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failureMessage, exception);
        }
    }

    private void setFieldPolicy(Long roleId, List<RoleFieldAccessRequest> accesses) throws Exception {
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/fields/roles/{roleId}/objects/CUSTOMER/access", roleId)
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

    private CustomerResponse replaceCollaborators(
            CustomerResponse customer, long actorId, String access) throws Exception {
        return fromJson(performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/customers/{customerId}/collaborators", customer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CustomerCollaboratorsUpdateRequest()
                                .setVersion(customer.getVersion())
                                .setCollaborators(List.of(new CustomerCollaboratorRequest()
                                        .setActorId(actorId).setAccess(access))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
    }

    private int customerCreateStatus(String name, String fieldCode, String value) throws Exception {
        CustomerCreateRequest request = new CustomerCreateRequest().setName(name)
                .setCustomFields(Map.of(fieldCode, List.of(value)));
        return performAs(OWNER_ACTOR_ID, TENANT_ID, post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON).content(toJson(request)))
                .andReturn().getResponse().getStatus();
    }

    private int parentUpdateStatus(CustomerResponse customer, Long parentId) throws Exception {
        return performAs(OWNER_ACTOR_ID, TENANT_ID,
                put("/api/v1/customers/{customerId}/parent", customer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new CustomerParentUpdateRequest()
                                .setVersion(customer.getVersion())
                                .setParentCustomerId(parentId))))
                .andReturn().getResponse().getStatus();
    }

    @SafeVarargs
    private final List<Integer> runConcurrently(java.util.concurrent.Callable<Integer>... tasks)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
        try {
            List<Future<Integer>> futures = java.util.Arrays.stream(tasks).map(task ->
                    executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("Timed out waiting to start customer writes");
                        }
                        return task.call();
                    })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<Integer> statuses = new java.util.ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(10, TimeUnit.SECONDS));
            }
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
        seedActiveMember(actorId, displayName, null);
    }

    private void seedActiveMember(long actorId, String displayName, Long departmentId) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                actorId, displayName);
        jdbc.update("INSERT INTO organization_membership "
                        + "(tenant_id, actor_id, department_id, status) VALUES (?, ?, ?, 'ACTIVE')",
                TENANT_ID, actorId, departmentId);
        jdbc.update("INSERT INTO authorization_role_assignment (tenant_id, actor_id, role_id) "
                        + "SELECT tenant_id, ?, id FROM authorization_role "
                        + "WHERE tenant_id = ? AND system_key = 'TENANT_MEMBER'",
                actorId, TENANT_ID);
    }

    private long insertDepartment(JdbcTemplate jdbc, long parentId, String name) {
        jdbc.update("INSERT INTO organization_department "
                        + "(tenant_id, parent_department_id, name, status) "
                        + "VALUES (?, ?, ?, 'ACTIVE')",
                TENANT_ID, parentId, name);
        return jdbc.queryForObject("SELECT id FROM organization_department "
                        + "WHERE tenant_id = ? AND parent_department_id = ? AND name = ?",
                Long.class, TENANT_ID, parentId, name);
    }

    private ResultActions performAs(
            long actorId, long tenantId, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("X-Dev-User-Id", actorId)
                .header("X-Dev-Tenant-Id", tenantId));
    }

    @Override
    protected void initTestData() {
        clearCustomerData();
    }

    @Override
    protected void clearTestData() {
        clearCustomerData();
    }

    private void clearCustomerData() {
        executeSql("DELETE FROM crm_activity_relation");
        executeSql("DELETE FROM crm_activity");
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
