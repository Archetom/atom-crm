package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.PasswordLoginOptionsRequest;
import io.github.archetom.crm.api.dto.request.PasswordSessionCreateRequest;
import io.github.archetom.crm.api.dto.request.UserCreateRequest;
import io.github.archetom.crm.api.dto.response.AuthenticationSessionResponse;
import io.github.archetom.crm.api.dto.response.PasswordLoginOptionsResponse;
import io.github.archetom.crm.api.dto.response.UserResponse;
import io.github.archetom.crm.infra.rest.security.AuthenticationCookieService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end browser password authentication and session invalidation coverage. */
@RequiresDatabase
class AuthenticationBrowserSessionIntegrationTest extends BaseIntegrationTest {

    private static final long OWNER_ACTOR_ID = 947001L;
    private static final long TENANT_ID = 947001L;
    private static final long TARGET_OWNER_ACTOR_ID = 947002L;
    private static final long TARGET_TENANT_ID = 947002L;
    private static final String USERNAME = "browser_auth_user";
    private static final String PASSWORD = "BrowserAuthPass1";

    @Test
    void passwordLoginSelectsAnActiveTenantAndInvalidatesTheCookieWhenTheAccountLocks() throws Exception {
        bootstrapTenant(OWNER_ACTOR_ID, TENANT_ID, "Auth Owner");
        bootstrapTenant(TARGET_OWNER_ACTOR_ID, TARGET_TENANT_ID, "Target Tenant Owner");
        UserResponse user = createUser();
        grantTargetTenantMembership(user.getId());

        PasswordLoginOptionsResponse options = fromJson(mockMvc.perform(post(
                        "/api/v1/authentication/password/login-options")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new PasswordLoginOptionsRequest()
                                .setTenantId(TENANT_ID)
                                .setLoginName(USERNAME)
                                .setPassword(PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), PasswordLoginOptionsResponse.class);
        assertNotNull(options);
        assertNotNull(options.getLoginChallenge());
        assertEquals(2, options.getTenants().size());
        assertTrue(options.getTenants().stream().anyMatch(tenant -> TENANT_ID == tenant.getTenantId()));
        assertTrue(options.getTenants().stream()
                .anyMatch(tenant -> TARGET_TENANT_ID == tenant.getTenantId()));

        MvcResult sessionCreation = mockMvc.perform(post("/api/v1/authentication/password/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new PasswordSessionCreateRequest()
                                .setLoginChallenge(options.getLoginChallenge())
                                .setTenantId(TARGET_TENANT_ID))))
                .andExpect(status().isOk())
                .andReturn();
        AuthenticationSessionResponse createdSession = fromJson(
                sessionCreation.getResponse().getContentAsString(), AuthenticationSessionResponse.class);
        assertNotNull(createdSession);
        assertEquals(user.getId(), createdSession.getActorId());
        assertEquals(TARGET_TENANT_ID, createdSession.getTenantId());
        Cookie sessionCookie = sessionCreation.getResponse().getCookie(AuthenticationCookieService.SESSION_COOKIE);
        Cookie csrfCookie = sessionCreation.getResponse().getCookie(AuthenticationCookieService.CSRF_COOKIE);
        assertNotNull(sessionCookie);
        assertNotNull(csrfCookie);
        assertTrue(sessionCookie.isHttpOnly());
        assertFalse(csrfCookie.isHttpOnly());

        mockMvc.perform(post("/api/v1/authentication/password/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new PasswordSessionCreateRequest()
                                .setLoginChallenge(options.getLoginChallenge())
                                .setTenantId(TARGET_TENANT_ID))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/authentication/session").cookie(sessionCookie, csrfCookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/authorization/me").cookie(sessionCookie, csrfCookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/navigation").cookie(sessionCookie, csrfCookie))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/authentication/session").cookie(sessionCookie, csrfCookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/authentication/session")
                        .cookie(sessionCookie, csrfCookie)
                        .header(AuthenticationCookieService.CSRF_HEADER, csrfCookie.getValue()))
                .andExpect(status().isNoContent());

        mockMvc.perform(asOwner(put("/api/v1/users/{userId}/status", user.getId())
                        .param("status", "LOCKED")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/authentication/session").cookie(sessionCookie, csrfCookie))
                .andExpect(status().isUnauthorized());
    }

    private void bootstrapTenant(long actorId, long tenantId, String displayName) throws Exception {
        mockMvc.perform(asActor(actorId, tenantId, post("/api/v1/organization/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OrganizationBootstrapRequest().setDisplayName(displayName)))))
                .andExpect(status().isOk());
    }

    private void grantTargetTenantMembership(long actorId) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update(
                "INSERT INTO organization_membership (tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')",
                TARGET_TENANT_ID, actorId);
        jdbc.update("INSERT INTO authorization_role_assignment (tenant_id, actor_id, role_id) "
                        + "SELECT tenant_id, ?, id FROM authorization_role "
                        + "WHERE tenant_id = ? AND system_key = 'TENANT_MEMBER'",
                actorId, TARGET_TENANT_ID);
    }

    private UserResponse createUser() throws Exception {
        return fromJson(mockMvc.perform(asOwner(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new UserCreateRequest()
                                .setUsername(USERNAME)
                                .setEmail("browser.auth.user@example.test")
                                .setPassword(PASSWORD)
                                .setRealName("Browser Auth User")))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), UserResponse.class);
    }

    private MockHttpServletRequestBuilder asOwner(MockHttpServletRequestBuilder request) {
        return asActor(OWNER_ACTOR_ID, TENANT_ID, request);
    }

    private MockHttpServletRequestBuilder asActor(
            long actorId, long tenantId, MockHttpServletRequestBuilder request) {
        return request.header("X-Dev-User-Id", actorId)
                .header("X-Dev-Tenant-Id", tenantId);
    }

    @Override
    protected void clearTestData() {
        clearTenantRolesAndMemberships(TARGET_TENANT_ID);
        clearTenantRolesAndMemberships(TENANT_ID);
        executeSql("DELETE FROM organization_member_profile WHERE actor_id IN (" + OWNER_ACTOR_ID + ", "
                + TARGET_OWNER_ACTOR_ID + ") OR actor_id IN (SELECT id FROM t_user WHERE tenant_id = "
                + TENANT_ID + ")");
        executeSql("DELETE FROM t_user WHERE tenant_id = " + TENANT_ID);
    }

    private void clearTenantRolesAndMemberships(long tenantId) {
        executeSql("DELETE FROM authorization_role_assignment WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM authorization_role_grant WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM authorization_role WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM organization_membership WHERE tenant_id = " + tenantId);
    }
}
