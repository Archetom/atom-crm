package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.OpportunityQueryRequest;
import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.ProductQueryRequest;
import io.github.archetom.crm.api.dto.request.RoleAssignmentCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldAccessRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldPolicyReplaceRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitQueryRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitCreateRequest;
import io.github.archetom.crm.api.dto.response.FieldDefinitionResponse;
import io.github.archetom.crm.api.dto.response.ReturnVisitResponse;
import io.github.archetom.crm.api.dto.response.TenantRoleResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MySQL/HTTP coverage for B16b field-aware keyword search. */
@RequiresDatabase
class B16bKeywordSearchIntegrationTest extends BaseIntegrationTest {

    private final Set<Long> createdTenants = new LinkedHashSet<>();
    private final Set<Long> createdActors = new LinkedHashSet<>();

    @Test
    void searchesOnlyActiveOrdinaryRowsFromTheVerifiedTenant() throws Exception {
        long actor = 816101L;
        long tenant = 816001L;
        long otherActor = 816102L;
        long otherTenant = 816002L;
        bootstrap(actor, tenant, "B16b isolation owner");
        bootstrap(otherActor, otherTenant, "B16b other owner");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        seedIsolationRows(jdbc, tenant, actor, "A");
        seedIsolationRows(jdbc, otherTenant, otherActor, "B");

        expectGetTotal(actor, tenant, "/api/v1/customers", "IsolationCustomer", 1);
        expectGetTotal(actor, tenant, "/api/v1/contacts", "IsolationContact", 1);
        expectGetTotal(actor, tenant, "/api/v1/leads", "IsolationLead", 1);
        expectPostTotal(actor, tenant, "/api/v1/opportunities/query",
                new OpportunityQueryRequest().setKeyword("IsolationOpportunity"), 1);
        expectPostTotal(actor, tenant, "/api/v1/products/query",
                new ProductQueryRequest().setKeyword("IsolationProduct"), 1);
        expectPostTotal(actor, tenant, "/api/v1/return-visits/query",
                new ReturnVisitQueryRequest().setKeyword("RV-ISOLATION"), 1);

        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_customer WHERE tenant_id = ? "
                        + "AND name LIKE 'IsolationCustomer%'", Integer.class, tenant));
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact WHERE tenant_id = ? "
                        + "AND name LIKE 'IsolationContact%'", Integer.class, tenant));
    }

    @Test
    void returnVisitListSupportsSafeServerSideSorting() throws Exception {
        long actor = 816121L;
        long tenant = 816021L;
        bootstrap(actor, tenant, "B16b return visit sort owner");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO crm_customer (tenant_id, owner_actor_id, name) VALUES (?, ?, ?)",
                tenant, actor, "Return visit sort customer");
        long customerId = jdbc.queryForObject(
                "SELECT id FROM crm_customer WHERE tenant_id = ? AND name = ?", Long.class,
                tenant, "Return visit sort customer");
        jdbc.update("INSERT INTO crm_return_visit "
                        + "(tenant_id, owner_actor_id, customer_id, visit_number, visit_time, status) "
                        + "VALUES (?, ?, ?, 'RV-SORT-LATER', ?, 'ACTIVE'), "
                        + "(?, ?, ?, 'RV-SORT-EARLIER', ?, 'ACTIVE')",
                tenant, actor, customerId, LocalDateTime.of(2031, 2, 2, 9, 0),
                tenant, actor, customerId, LocalDateTime.of(2031, 1, 1, 9, 0));
        long earlier = jdbc.queryForObject(
                "SELECT id FROM crm_return_visit WHERE tenant_id = ? AND visit_number = ?", Long.class,
                tenant, "RV-SORT-EARLIER");
        long later = jdbc.queryForObject(
                "SELECT id FROM crm_return_visit WHERE tenant_id = ? AND visit_number = ?", Long.class,
                tenant, "RV-SORT-LATER");

        performAs(actor, tenant, post("/api/v1/return-visits/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ReturnVisitQueryRequest()
                                .setSortField("VISIT_TIME").setSortDirection("ASC"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(earlier))
                .andExpect(jsonPath("$.objectList[1].id").value(later));
        performAs(actor, tenant, post("/api/v1/return-visits/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ReturnVisitQueryRequest()
                                .setSortField("REMARK").setSortDirection("ASC"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnVisitCustomNumericFieldSortUsesCanonicalNumericValues() throws Exception {
        long actor = 816131L;
        long tenant = 816031L;
        bootstrap(actor, tenant, "B16b return visit custom sort owner");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO crm_customer (tenant_id, owner_actor_id, name) VALUES (?, ?, ?)",
                tenant, actor, "Return visit custom sort customer");
        long customerId = jdbc.queryForObject(
                "SELECT id FROM crm_customer WHERE tenant_id = ? AND name = ?", Long.class,
                tenant, "Return visit custom sort customer");
        FieldDefinitionResponse score = fromJson(performAs(actor, tenant,
                post("/api/v1/fields/objects/RETURN_VISIT").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new FieldCreateRequest().setDisplayName("Visit score")
                                .setFieldType("INTEGER"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        ReturnVisitResponse ten = fromJson(performAs(actor, tenant,
                post("/api/v1/return-visits").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ReturnVisitCreateRequest().setCustomerId(customerId)
                                .setVisitTime(LocalDateTime.of(2031, 2, 2, 9, 0))
                                .setRemark("Ten").setCustomFields(Map.of(score.getCode(), List.of("10"))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReturnVisitResponse.class);
        ReturnVisitResponse two = fromJson(performAs(actor, tenant,
                post("/api/v1/return-visits").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ReturnVisitCreateRequest().setCustomerId(customerId)
                                .setVisitTime(LocalDateTime.of(2031, 2, 1, 9, 0))
                                .setRemark("Two").setCustomFields(Map.of(score.getCode(), List.of("2"))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReturnVisitResponse.class);

        performAs(actor, tenant, post("/api/v1/return-visits/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ReturnVisitQueryRequest()
                                .setCustomSortFieldCode(score.getCode()).setSortDirection("ASC"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(two.getId()))
                .andExpect(jsonPath("$.objectList[1].id").value(ten.getId()));
        performAs(actor, tenant, post("/api/v1/return-visits/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ReturnVisitQueryRequest().setSortField("VISIT_NUMBER")
                                .setCustomSortFieldCode(score.getCode()).setSortDirection("ASC"))))
                .andExpect(status().isBadRequest());
        performAs(actor, tenant, post("/api/v1/return-visits/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ReturnVisitQueryRequest().setCustomSortFieldCode("visit_number")
                                .setSortDirection("ASC"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void intersectsKeywordCandidatesWithScopeAndCurrentFieldPolicy() throws Exception {
        long owner = 816111L;
        long tenant = 816011L;
        long member = 816211L;
        bootstrap(owner, tenant, "B16b policy owner");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        seedMember(jdbc, tenant, member, "B16b scoped member");
        long customerId = seedScopedRows(jdbc, tenant, owner, member);
        TenantRoleResponse role = createReaderRole(owner, tenant);
        assign(owner, tenant, role.getId(), member);
        replacePolicy(owner, tenant, role.getId(), "CUSTOMER", List.of(
                access("customer_name", "READ", "NONE"),
                access("phone_number", "READ", "PARTIAL")));
        replacePolicy(owner, tenant, role.getId(), "CONTACT", List.of(
                access("contact_name", "READ", "NONE"),
                access("phone_number", "READ", "FULL")));
        replacePolicy(owner, tenant, role.getId(), "LEAD", List.of(
                access("lead_name", "READ", "NONE"),
                access("phone_number", "READ", "PARTIAL")));
        replacePolicy(owner, tenant, role.getId(), "OPPORTUNITY", List.of(
                access("opportunity_name", "READ", "PARTIAL")));
        replacePolicy(owner, tenant, role.getId(), "PRODUCT", List.of(
                access("product_name", "READ", "NONE"),
                access("product_code", "READ", "FULL")));
        replacePolicy(owner, tenant, role.getId(), "RETURN_VISIT", List.of(
                access("visit_number", "HIDDEN", "NONE")));

        expectGetTotal(member, tenant, "/api/v1/customers", "OwnCustomer", 1);
        expectGetTotal(member, tenant, "/api/v1/customers", "HiddenCustomerPhone", 0);
        expectGetTotal(member, tenant, "/api/v1/customers", "ScopeOnlyCustomer", 0);
        expectGetTotal(member, tenant, "/api/v1/contacts", "OwnContact", 1);
        expectGetTotal(member, tenant, "/api/v1/contacts", "HiddenContactPhone", 0);
        expectGetTotal(member, tenant, "/api/v1/leads", "OwnLead", 1);
        expectGetTotal(member, tenant, "/api/v1/leads", "HiddenLeadPhone", 0);
        expectPostTotal(member, tenant, "/api/v1/opportunities/query",
                new OpportunityQueryRequest().setKeyword("OwnOpportunity"), 0);
        expectPostTotal(member, tenant, "/api/v1/products/query",
                new ProductQueryRequest().setKeyword("OwnProduct"), 1);
        expectPostTotal(member, tenant, "/api/v1/products/query",
                new ProductQueryRequest().setKeyword(" ＳＫＵ－１ "), 0);
        expectPostTotal(member, tenant, "/api/v1/return-visits/query",
                new ReturnVisitQueryRequest().setKeyword("RV-OWN"), 0);

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_return_visit WHERE tenant_id = ? "
                        + "AND customer_id = ? AND visit_number = 'RV-OWN'",
                Integer.class, tenant, customerId));
    }

    private void seedIsolationRows(JdbcTemplate jdbc, long tenant, long actor, String suffix) {
        OpportunityPipelineFixture.provisionActivePair(jdbc, tenant, "direct", "qualification");
        jdbc.update("INSERT INTO crm_customer "
                        + "(tenant_id, owner_actor_id, name, status) VALUES "
                        + "(?, ?, 'IsolationCustomerActive', 'ACTIVE'), "
                        + "(?, NULL, 'IsolationCustomerPool', 'IN_POOL'), "
                        + "(?, ?, 'IsolationCustomerDeleted', 'DELETED')",
                tenant, actor, tenant, tenant, actor);
        Long customerId = jdbc.queryForObject(
                "SELECT id FROM crm_customer WHERE tenant_id = ? "
                        + "AND name = 'IsolationCustomerActive'", Long.class, tenant);
        jdbc.update("INSERT INTO crm_contact "
                        + "(tenant_id, owner_actor_id, name, status) VALUES "
                        + "(?, ?, 'IsolationContactActive', 'ACTIVE'), "
                        + "(?, NULL, 'IsolationContactPool', 'IN_POOL'), "
                        + "(?, ?, 'IsolationContactDeleted', 'DELETED')",
                tenant, actor, tenant, tenant, actor);
        jdbc.update("INSERT INTO crm_lead "
                        + "(tenant_id, owner_actor_id, name, status) VALUES "
                        + "(?, ?, 'IsolationLeadActive', 'ACTIVE'), "
                        + "(?, ?, 'IsolationLeadDeleted', 'DELETED')",
                tenant, actor, tenant, actor);
        jdbc.update("INSERT INTO crm_opportunity "
                        + "(tenant_id, owner_actor_id, customer_id, name, pipeline_code, stage_code, status) "
                        + "VALUES (?, ?, ?, 'IsolationOpportunityActive', 'direct', 'qualification', 'ACTIVE'), "
                        + "(?, ?, ?, 'IsolationOpportunityDeleted', 'direct', 'qualification', 'DELETED')",
                tenant, actor, customerId, tenant, actor, customerId);
        jdbc.update("INSERT INTO crm_product "
                        + "(tenant_id, owner_actor_id, name, code, code_key, status) VALUES "
                        + "(?, ?, 'IsolationProductActive', ?, ?, 'ACTIVE'), "
                        + "(?, ?, 'IsolationProductDeleted', ?, ?, 'DELETED')",
                tenant, actor, "ISO-A-" + suffix, "iso-a-" + suffix.toLowerCase(),
                tenant, actor, "ISO-D-" + suffix, "iso-d-" + suffix.toLowerCase());
        jdbc.update("INSERT INTO crm_return_visit "
                        + "(tenant_id, owner_actor_id, customer_id, visit_number, visit_time, status) "
                        + "VALUES (?, ?, ?, 'RV-ISOLATION-ACTIVE', ?, 'ACTIVE'), "
                        + "(?, ?, ?, 'RV-ISOLATION-DELETED', ?, 'DELETED')",
                tenant, actor, customerId, LocalDateTime.of(2026, 7, 18, 9, 0),
                tenant, actor, customerId, LocalDateTime.of(2026, 7, 18, 10, 0));
    }

    private long seedScopedRows(JdbcTemplate jdbc, long tenant, long owner, long member) {
        OpportunityPipelineFixture.provisionActivePair(jdbc, tenant, "direct", "qualification");
        jdbc.update("INSERT INTO crm_customer "
                        + "(tenant_id, owner_actor_id, name, phone_number, status) VALUES "
                        + "(?, ?, 'OwnCustomer', 'HiddenCustomerPhone', 'ACTIVE'), "
                        + "(?, ?, 'ScopeOnlyCustomer', NULL, 'ACTIVE')",
                tenant, member, tenant, owner);
        Long customerId = jdbc.queryForObject(
                "SELECT id FROM crm_customer WHERE tenant_id = ? AND name = 'OwnCustomer'",
                Long.class, tenant);
        jdbc.update("INSERT INTO crm_contact "
                        + "(tenant_id, owner_actor_id, name, phone_number, status) "
                        + "VALUES (?, ?, 'OwnContact', 'HiddenContactPhone', 'ACTIVE')",
                tenant, member);
        jdbc.update("INSERT INTO crm_lead "
                        + "(tenant_id, owner_actor_id, name, phone_number, status) "
                        + "VALUES (?, ?, 'OwnLead', 'HiddenLeadPhone', 'ACTIVE')",
                tenant, member);
        jdbc.update("INSERT INTO crm_opportunity "
                        + "(tenant_id, owner_actor_id, customer_id, name, pipeline_code, stage_code, status) "
                        + "VALUES (?, ?, ?, 'OwnOpportunity', 'direct', 'qualification', 'ACTIVE')",
                tenant, member, customerId);
        jdbc.update("INSERT INTO crm_product "
                        + "(tenant_id, owner_actor_id, name, code, code_key, status) "
                        + "VALUES (?, ?, 'OwnProduct', 'SKU-1', 'sku-1', 'ACTIVE')",
                tenant, member);
        jdbc.update("INSERT INTO crm_return_visit "
                        + "(tenant_id, owner_actor_id, customer_id, visit_number, visit_time, status) "
                        + "VALUES (?, ?, ?, 'RV-OWN', ?, 'ACTIVE')",
                tenant, member, customerId, LocalDateTime.of(2026, 7, 18, 11, 0));
        return customerId;
    }

    private TenantRoleResponse createReaderRole(long owner, long tenant) throws Exception {
        return fromJson(performAs(owner, tenant, post("/api/v1/authorization/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleCreateRequest().setName("B16b scoped reader")
                                .setGrants(List.of(
                                        scoped("customers:read"), scoped("contacts:read"),
                                        scoped("leads:read"), scoped("opportunities:read"),
                                        new RoleGrantRequest().setAuthorityCode("products:read"),
                                        scoped("return-visits:read"))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                TenantRoleResponse.class);
    }

    private RoleGrantRequest scoped(String authority) {
        return new RoleGrantRequest().setAuthorityCode(authority).setDataScope("SELF");
    }

    private void assign(long owner, long tenant, long roleId, long member) throws Exception {
        performAs(owner, tenant, post("/api/v1/authorization/roles/{id}/assignments", roleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new RoleAssignmentCreateRequest().setActorIds(List.of(member)))))
                .andExpect(status().isOk());
    }

    private void replacePolicy(
            long owner, long tenant, long roleId, String objectType,
            List<RoleFieldAccessRequest> accesses) throws Exception {
        performAs(owner, tenant,
                put("/api/v1/fields/roles/{id}/objects/{type}/access", roleId, objectType)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleFieldPolicyReplaceRequest().setAccesses(accesses))))
                .andExpect(status().isOk());
    }

    private RoleFieldAccessRequest access(String code, String level, String mask) {
        return new RoleFieldAccessRequest().setFieldCode(code)
                .setAccessLevel(level).setMaskType(mask);
    }

    private void expectGetTotal(
            long actor, long tenant, String path, String keyword, int total) throws Exception {
        performAs(actor, tenant, get(path).param("keyword", keyword))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(total));
    }

    private void expectPostTotal(
            long actor, long tenant, String path, Object request, int total) throws Exception {
        performAs(actor, tenant, post(path).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(total));
    }

    private void seedMember(JdbcTemplate jdbc, long tenant, long actor, String displayName) {
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                actor, displayName);
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) "
                + "VALUES (?, ?, 'ACTIVE')", tenant, actor);
        jdbc.update("INSERT INTO authorization_role_assignment (tenant_id, actor_id, role_id) "
                        + "SELECT tenant_id, ?, id FROM authorization_role "
                        + "WHERE tenant_id = ? AND system_key = 'TENANT_MEMBER'",
                actor, tenant);
        createdActors.add(actor);
    }

    private void bootstrap(long actor, long tenant, String displayName) throws Exception {
        performAs(actor, tenant, post("/api/v1/organization/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName(displayName))))
                .andExpect(status().isOk());
        createdTenants.add(tenant);
        createdActors.add(actor);
    }

    private ResultActions performAs(
            long actor, long tenant, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("X-Dev-User-Id", actor)
                .header("X-Dev-Tenant-Id", tenant));
    }

    @Override
    protected void clearTestData() {
        if (dataSource == null || createdTenants.isEmpty()) return;
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (Long tenant : createdTenants) {
            deleteTenantRows(jdbc, "crm_return_visit_unique_field_value", tenant);
            deleteTenantRows(jdbc, "crm_return_visit_field_value", tenant);
            deleteTenantRows(jdbc, "crm_return_visit", tenant);
            deleteTenantRows(jdbc, "crm_opportunity_star", tenant);
            deleteTenantRows(jdbc, "crm_opportunity_unique_field_value", tenant);
            deleteTenantRows(jdbc, "crm_opportunity_field_value", tenant);
            deleteTenantRows(jdbc, "crm_opportunity_product_line", tenant);
            deleteTenantRows(jdbc, "crm_opportunity_collaborator", tenant);
            deleteTenantRows(jdbc, "crm_opportunity_contact", tenant);
            deleteTenantRows(jdbc, "crm_opportunity", tenant);
            deleteTenantRows(jdbc, "crm_contact_star", tenant);
            deleteTenantRows(jdbc, "crm_contact_unique_field_value", tenant);
            deleteTenantRows(jdbc, "crm_contact_field_value", tenant);
            deleteTenantRows(jdbc, "crm_contact_collaborator", tenant);
            deleteTenantRows(jdbc, "crm_contact_tag", tenant);
            deleteTenantRows(jdbc, "crm_contact_address", tenant);
            deleteTenantRows(jdbc, "crm_contact", tenant);
            deleteTenantRows(jdbc, "crm_lead_star", tenant);
            deleteTenantRows(jdbc, "crm_lead_unique_field_value", tenant);
            deleteTenantRows(jdbc, "crm_lead_field_value", tenant);
            deleteTenantRows(jdbc, "crm_lead_collaborator", tenant);
            deleteTenantRows(jdbc, "crm_lead_tag", tenant);
            deleteTenantRows(jdbc, "crm_lead_address", tenant);
            deleteTenantRows(jdbc, "crm_lead", tenant);
            deleteTenantRows(jdbc, "crm_product_unique_field_value", tenant);
            deleteTenantRows(jdbc, "crm_product_field_value", tenant);
            deleteTenantRows(jdbc, "crm_product", tenant);
            deleteTenantRows(jdbc, "crm_customer_star", tenant);
            deleteTenantRows(jdbc, "crm_customer_unique_field_value", tenant);
            deleteTenantRows(jdbc, "crm_customer_field_value", tenant);
            deleteTenantRows(jdbc, "crm_customer_collaborator", tenant);
            deleteTenantRows(jdbc, "crm_customer_tag", tenant);
            deleteTenantRows(jdbc, "crm_customer_address", tenant);
            deleteTenantRows(jdbc, "crm_customer", tenant);
            deleteTenantRows(jdbc, "role_field_access", tenant);
            deleteTenantRows(jdbc, "role_field_policy", tenant);
            deleteTenantRows(jdbc, "field_layout_preference", tenant);
            deleteTenantRows(jdbc, "custom_field_definition", tenant);
            deleteTenantRows(jdbc, "customization_object_guard", tenant);
            deleteTenantRows(jdbc, "navigation_preference", tenant);
            deleteTenantRows(jdbc, "crm_activity", tenant);
            deleteTenantRows(jdbc, "authorization_role_assignment", tenant);
            deleteTenantRows(jdbc, "authorization_role_grant", tenant);
            deleteTenantRows(jdbc, "authorization_role", tenant);
            deleteTenantRows(jdbc, "organization_membership", tenant);
            deleteTenantRows(jdbc, "organization_department", tenant);
            deleteTenantRows(jdbc, "organization_tenant", tenant);
        }
        for (Long actor : createdActors) {
            jdbc.update("DELETE FROM organization_member_profile WHERE actor_id = ?", actor);
        }
        createdTenants.clear();
        createdActors.clear();
    }

    private void deleteTenantRows(JdbcTemplate jdbc, String table, long tenant) {
        jdbc.update("DELETE FROM " + table + " WHERE tenant_id = ?", tenant);
    }
}
