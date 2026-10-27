package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.DashboardPreferenceUpdateRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.response.DashboardPreferenceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MySQL and HTTP coverage for B18a current-member dashboard defaults. */
@RequiresDatabase
class DashboardPreferenceIntegrationTest extends BaseIntegrationTest {

    private static final long ACTOR_ID = 888888L;
    private static final long TENANT_ID = 999999L;

    @Test
    void preferencesAreTypedVersionedAndIsolatedByVerifiedTenantAndMember() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/preferences"))
                .andExpect(status().isUnauthorized());
        performGet("/api/v1/dashboard/preferences")
                .andExpect(status().isForbidden());

        bootstrap(ACTOR_ID, TENANT_ID, "Dashboard owner");
        DashboardPreferenceResponse defaults = preference(ACTOR_ID, TENANT_ID);
        assertEquals("CURRENT_MONTH", defaults.getTimeRange());
        assertEquals("SELF", defaults.getDataScope());
        assertEquals(List.of(), defaults.getHiddenModules());
        assertNull(defaults.getVersion());

        DashboardPreferenceResponse saved = update(ACTOR_ID, TENANT_ID,
                new DashboardPreferenceUpdateRequest()
                        .setTimeRange("current_quarter")
                        .setDataScope("department_subtree")
                        .setHiddenModules(List.of("customer_contact_reminder")));
        assertEquals("CURRENT_QUARTER", saved.getTimeRange());
        assertEquals("DEPARTMENT_SUBTREE", saved.getDataScope());
        assertEquals(List.of("CUSTOMER_CONTACT_REMINDER"), saved.getHiddenModules());
        assertEquals(0L, saved.getVersion());

        DashboardPreferenceUpdateRequest stale = new DashboardPreferenceUpdateRequest()
                .setTimeRange("CURRENT_YEAR")
                .setDataScope("TENANT");
        performAs(ACTOR_ID, TENANT_ID, put("/api/v1/dashboard/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(stale)))
                .andExpect(status().isConflict());

        DashboardPreferenceResponse custom = update(ACTOR_ID, TENANT_ID,
                new DashboardPreferenceUpdateRequest()
                        .setTimeRange("CUSTOM")
                        .setCustomStartDate(LocalDate.of(2026, 1, 1))
                        .setCustomEndDate(LocalDate.of(2026, 12, 31))
                        .setDataScope("TENANT")
                        .setHiddenModules(List.of("CUSTOMER_CONTACT_REMINDER"))
                        .setVersion(saved.getVersion()));
        assertEquals(1L, custom.getVersion());
        assertEquals(LocalDate.of(2026, 12, 31), custom.getCustomEndDate());
        assertEquals(List.of("CUSTOMER_CONTACT_REMINDER"), custom.getHiddenModules());

        performAs(ACTOR_ID, TENANT_ID, put("/api/v1/dashboard/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new DashboardPreferenceUpdateRequest()
                        .setTimeRange("CURRENT_MONTH")
                        .setDataScope("SELF")
                        .setHiddenModules(List.of("UNKNOWN_MODULE"))
                        .setVersion(custom.getVersion()))))
                .andExpect(status().isBadRequest());

        performAs(ACTOR_ID, TENANT_ID, put("/api/v1/dashboard/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new DashboardPreferenceUpdateRequest()
                        .setTimeRange("CURRENT_MONTH")
                        .setCustomStartDate(LocalDate.of(2026, 1, 1))
                        .setCustomEndDate(LocalDate.of(2026, 1, 2))
                        .setDataScope("SELF")
                        .setHiddenModules(List.of())
                        .setVersion(custom.getVersion()))))
                .andExpect(status().isUnprocessableEntity());

        long otherActor = 777777L;
        long otherTenant = 777001L;
        bootstrap(otherActor, otherTenant, "Other dashboard owner");
        DashboardPreferenceResponse otherDefaults = preference(otherActor, otherTenant);
        assertNull(otherDefaults.getVersion());
        assertEquals("SELF", otherDefaults.getDataScope());
        assertEquals(List.of(), otherDefaults.getHiddenModules());
        assertEquals("CUSTOM", preference(ACTOR_ID, TENANT_ID).getTimeRange());
    }

    @Test
    void domainAndDatabaseConstraintsRejectUnboundedCustomRanges() throws Exception {
        bootstrap(ACTOR_ID, TENANT_ID, "Dashboard constraint owner");

        performAs(ACTOR_ID, TENANT_ID, put("/api/v1/dashboard/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new DashboardPreferenceUpdateRequest()
                        .setTimeRange("CUSTOM")
                        .setCustomStartDate(LocalDate.of(2026, 1, 1))
                        .setCustomEndDate(LocalDate.of(2027, 1, 2))
                        .setDataScope("SELF"))))
                .andExpect(status().isUnprocessableEntity());

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO dashboard_preference "
                        + "(tenant_id, actor_id, time_range, custom_start_date, custom_end_date, data_scope) "
                        + "VALUES (?, ?, 'CUSTOM', '2026-01-01', '2027-01-02', 'SELF')",
                TENANT_ID, ACTOR_ID));
    }

    private DashboardPreferenceResponse preference(long actorId, long tenantId) throws Exception {
        return fromJson(performAs(actorId, tenantId,
                        get("/api/v1/dashboard/preferences"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                DashboardPreferenceResponse.class);
    }

    private DashboardPreferenceResponse update(
            long actorId, long tenantId, DashboardPreferenceUpdateRequest request) throws Exception {
        return fromJson(performAs(actorId, tenantId,
                        put("/api/v1/dashboard/preferences")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(request)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                DashboardPreferenceResponse.class);
    }

    private void bootstrap(long actorId, long tenantId, String displayName) throws Exception {
        performAs(actorId, tenantId, post("/api/v1/organization/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName(displayName))))
                .andExpect(status().isOk());
    }

    private ResultActions performAs(
            long actorId, long tenantId, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request
                .header("X-Dev-User-Id", actorId)
                .header("X-Dev-Tenant-Id", tenantId));
    }

    @Override
    protected void initTestData() {
        clearData();
    }

    @Override
    protected void clearTestData() {
        clearData();
    }

    private void clearData() {
        executeSql("DELETE FROM dashboard_preference");
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
