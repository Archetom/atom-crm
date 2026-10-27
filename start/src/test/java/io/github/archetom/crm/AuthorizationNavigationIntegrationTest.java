package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.NavigationPreferenceUpdateRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.RoleAssignmentCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantRequest;
import io.github.archetom.crm.api.dto.response.MemberAuthorizationResponse;
import io.github.archetom.crm.api.dto.response.NavigationItemResponse;
import io.github.archetom.crm.api.dto.response.NavigationPreferenceResponse;
import io.github.archetom.crm.api.dto.response.TenantRoleResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.core.type.TypeReference;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.github.archetom.crm.domain.policy.AuthorityCatalog.ORGANIZATION_REQUEST;
import static io.github.archetom.crm.domain.policy.AuthorityCatalog.WORKFLOWS_MANAGE;
import static io.github.archetom.crm.domain.policy.AuthorityCatalog.WORKFLOWS_READ;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end coverage for resolved tenant authorization and presentation-only navigation. */
@RequiresDatabase
class AuthorizationNavigationIntegrationTest extends BaseIntegrationTest {

    private static final long OWNER_ACTOR_ID = 888888L;
    private static final long TENANT_ID = 999999L;

    @Test
    void authorizationChangesAreResolvedOnFollowingRequests() throws Exception {
        mockMvc.perform(get("/api/v1/authorization/me"))
                .andExpect(status().isUnauthorized());
        performGet("/api/v1/authorization/me")
                .andExpect(status().isForbidden());

        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Tenant Owner");
        MemberAuthorizationResponse authorization = fromJson(
                performGet("/api/v1/authorization/me")
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                MemberAuthorizationResponse.class);
        assertTrue(authorization.getRoles().stream()
                .filter(role -> "TENANT_OWNER".equals(role.getSystemKey()))
                .anyMatch(role -> role.getGrants() != null && !role.getGrants().isEmpty()));

        long targetActorId = 777777L;
        seedActiveMember(targetActorId, "Target Member");
        performAs(targetActorId, TENANT_ID, get("/api/v1/authorization/roles"))
                .andExpect(status().isForbidden());

        TenantRoleResponse readerRole = createRole(OWNER_ACTOR_ID, TENANT_ID, "Role Readers",
                "authorization:read");
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                post("/api/v1/authorization/roles/{roleId}/assignments", readerRole.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RoleAssignmentCreateRequest().setActorIds(List.of(targetActorId)))))
                .andExpect(status().isOk());

        performAs(targetActorId, TENANT_ID, get("/api/v1/authorization/roles"))
                .andExpect(status().isOk());

        performAs(OWNER_ACTOR_ID, TENANT_ID,
                delete("/api/v1/authorization/roles/{roleId}/assignments/{actorId}",
                        readerRole.getId(), targetActorId))
                .andExpect(status().isOk());
        performAs(targetActorId, TENANT_ID, get("/api/v1/authorization/roles"))
                .andExpect(status().isForbidden());
    }

    @Test
    void navigationPreferencesOnlyNarrowPresentationAndTenantLookupsStayScoped() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Tenant Owner");

        List<NavigationItemResponse> initialNavigation = navigationForOwner();
        assertTrue(hasNavigationCode(initialNavigation, "administration"));

        NavigationPreferenceResponse hidden = fromJson(
                performPut("/api/v1/navigation/preferences",
                        new NavigationPreferenceUpdateRequest().setHiddenCodes(List.of("administration")))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                NavigationPreferenceResponse.class);
        assertFalse(hasNavigationCode(navigationForOwner(), "administration"));
        assertTrue(hasNavigationCode(hidden.getAvailableItems(), "administration"));
        performGet("/api/v1/authorization/roles")
                .andExpect(status().isOk());

        NavigationPreferenceUpdateRequest restore = new NavigationPreferenceUpdateRequest()
                .setVersion(hidden.getVersion());
        NavigationPreferenceResponse restored = fromJson(
                performPut("/api/v1/navigation/preferences", restore)
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                NavigationPreferenceResponse.class);
        assertTrue(restored.getVersion() > hidden.getVersion());
        performPut("/api/v1/navigation/preferences", restore)
                .andExpect(status().isConflict());

        long otherActorId = 666666L;
        long otherTenantId = 666001L;
        bootstrap(otherActorId, otherTenantId, "Other Owner");
        TenantRoleResponse otherRole = createRole(otherActorId, otherTenantId, "Other Role",
                "authorization:read");
        performGet("/api/v1/authorization/roles/{roleId}/assignments", otherRole.getId())
                .andExpect(status().isNotFound());
    }

    @Test
    void workflowAuthoritiesAreTenantAssignableAndProduceTheWorkflowNavigationEntry() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Tenant Owner");
        long workflowActorId = 666667L;
        seedActiveMember(workflowActorId, "Workflow Member");
        TenantRoleResponse reader = createRole(OWNER_ACTOR_ID, TENANT_ID,
                "Workflow readers", WORKFLOWS_READ);
        TenantRoleResponse manager = createRole(OWNER_ACTOR_ID, TENANT_ID,
                "Workflow managers", WORKFLOWS_MANAGE);
        TenantRoleResponse navigation = createRole(OWNER_ACTOR_ID, TENANT_ID,
                "Workflow navigation", "navigation:read");
        for (TenantRoleResponse role : List.of(reader, manager, navigation)) {
            performAs(OWNER_ACTOR_ID, TENANT_ID,
                    post("/api/v1/authorization/roles/{roleId}/assignments", role.getId())
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(
                                    new RoleAssignmentCreateRequest().setActorIds(
                                            List.of(workflowActorId)))))
                    .andExpect(status().isOk());
        }

        MemberAuthorizationResponse authorization = fromJson(performAs(workflowActorId, TENANT_ID,
                get("/api/v1/authorization/me")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), MemberAuthorizationResponse.class);
        assertTrue(authorization.getAuthorities().contains(WORKFLOWS_READ));
        assertTrue(authorization.getAuthorities().contains(WORKFLOWS_MANAGE));
        List<NavigationItemResponse> navigationItems = fromJson(performAs(workflowActorId,
                TENANT_ID, get("/api/v1/navigation")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        assertTrue(hasNavigationCode(navigationItems, "workflow.definitions"));
    }

    @Test
    void targetReadAuthorityProducesOnlyItsServerDefinedNavigationEntry() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Tenant Owner");
        long targetActorId = 666669L;
        seedActiveMember(targetActorId, "Target Member");
        for (String authority : List.of("navigation:read", "dashboard-targets:read")) {
            TenantRoleResponse role = createRole(OWNER_ACTOR_ID, TENANT_ID,
                    "Target " + authority, authority);
            performAs(OWNER_ACTOR_ID, TENANT_ID,
                    post("/api/v1/authorization/roles/{roleId}/assignments", role.getId())
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(
                                    new RoleAssignmentCreateRequest().setActorIds(List.of(targetActorId)))))
                    .andExpect(status().isOk());
        }

        MemberAuthorizationResponse authorization = fromJson(performAs(targetActorId, TENANT_ID,
                get("/api/v1/authorization/me")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), MemberAuthorizationResponse.class);
        assertTrue(authorization.getAuthorities().contains(ORGANIZATION_REQUEST));

        List<NavigationItemResponse> navigationItems = fromJson(performAs(targetActorId,
                TENANT_ID, get("/api/v1/navigation")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        assertTrue(hasNavigationCode(navigationItems, "organization.tenants"));
        assertTrue(hasNavigationCode(navigationItems, "administration.performance-targets"));
        assertFalse(hasNavigationCode(navigationItems, "administration.users"));
    }

    @Test
    void p6ReadAuthoritiesProduceOnlyTheirServerDefinedNavigationEntries() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Tenant Owner");
        long p6ActorId = 666668L;
        seedActiveMember(p6ActorId, "P6 Member");
        for (String authority : List.of("navigation:read", "reports:read", "oa-forms:read",
                "oa-requests:read", "oa-announcements:read")) {
            TenantRoleResponse role = createRole(OWNER_ACTOR_ID, TENANT_ID,
                    "P6 " + authority, authority);
            performAs(OWNER_ACTOR_ID, TENANT_ID,
                    post("/api/v1/authorization/roles/{roleId}/assignments", role.getId())
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(
                                    new RoleAssignmentCreateRequest().setActorIds(List.of(p6ActorId)))))
                    .andExpect(status().isOk());
        }

        List<NavigationItemResponse> navigationItems = fromJson(performAs(p6ActorId,
                TENANT_ID, get("/api/v1/navigation")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        assertTrue(hasNavigationCode(navigationItems, "reports"));
        assertTrue(hasNavigationCode(navigationItems, "oa.forms"));
        assertTrue(hasNavigationCode(navigationItems, "oa.requests"));
        assertTrue(hasNavigationCode(navigationItems, "oa.announcements"));
        assertFalse(hasNavigationCode(navigationItems, "marketing.forms"));
    }

    @Test
    void roleKeywordSearchKeepsSubstringSemanticsAndTreatsWildcardsLiterally() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Tenant Owner");
        createRole(OWNER_ACTOR_ID, TENANT_ID, "Ops%Literal", "authorization:read");
        createRole(OWNER_ACTOR_ID, TENANT_ID, "Ops_Literal", "authorization:read");
        createRole(OWNER_ACTOR_ID, TENANT_ID, "Ops!Literal", "authorization:read");

        performAs(OWNER_ACTOR_ID, TENANT_ID,
                get("/api/v1/authorization/roles").param("keyword", "Ops%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].name").value("Ops%Literal"));
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                get("/api/v1/authorization/roles").param("keyword", "Ops_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].name").value("Ops_Literal"));
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                get("/api/v1/authorization/roles").param("keyword", "Ops!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].name").value("Ops!Literal"));
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                get("/api/v1/authorization/roles").param("keyword", "Literal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(3));
    }

    private void bootstrap(long actorId, long tenantId, String displayName) throws Exception {
        performAs(actorId, tenantId, post("/api/v1/organization/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName(displayName))))
                .andExpect(status().isOk());
    }

    private TenantRoleResponse createRole(long actorId, long tenantId, String name, String authority)
            throws Exception {
        RoleCreateRequest request = new RoleCreateRequest().setName(name)
                .setGrants(List.of(new RoleGrantRequest().setAuthorityCode(authority)));
        String json = performAs(actorId, tenantId, post("/api/v1/authorization/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return fromJson(json, TenantRoleResponse.class);
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

    private List<NavigationItemResponse> navigationForOwner() throws Exception {
        return fromJson(performGet("/api/v1/navigation")
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<>() { });
    }

    private boolean hasNavigationCode(List<NavigationItemResponse> items, String code) {
        return items != null && items.stream().anyMatch(item -> code.equals(item.getCode())
                || hasNavigationCode(item.getChildren(), code));
    }

    private ResultActions performAs(
            long actorId, long tenantId, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request
                .header("X-Dev-User-Id", actorId)
                .header("X-Dev-Tenant-Id", tenantId));
    }

    @Override
    protected void initTestData() {
        clearAuthorizationData();
    }

    @Override
    protected void clearTestData() {
        clearAuthorizationData();
    }

    private void clearAuthorizationData() {
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
