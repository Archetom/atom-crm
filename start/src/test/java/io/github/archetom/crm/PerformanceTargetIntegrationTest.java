package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetCreateRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetImportItemRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetImportRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetUpdateRequest;
import io.github.archetom.crm.api.dto.response.DashboardOverviewResponse;
import io.github.archetom.crm.api.dto.response.PerformanceTargetImportResponse;
import io.github.archetom.crm.api.dto.response.PerformanceTargetListResponse;
import io.github.archetom.crm.api.dto.response.PerformanceTargetResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MySQL and HTTP coverage for member monthly contract amount targets. */
@RequiresDatabase
@Import(PerformanceTargetIntegrationTest.FixedClockConfiguration.class)
class PerformanceTargetIntegrationTest extends BaseIntegrationTest {

    private static final long TENANT_ID = 1_718_500L;
    private static final long OWNER_ID = 1_725_001L;
    private static final long MANAGER_ID = 1_725_002L;
    private static final long READER_ID = 1_725_003L;
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 7, 18);

    @Test
    void managesVersionedMemberTargetsAndProjectsOnlyTheSameSelfDashboardScope() throws Exception {
        bootstrap();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long departmentId = department(jdbc, "B14a Sales");
        member(jdbc, MANAGER_ID, departmentId, "B14a manager");
        member(jdbc, READER_ID, departmentId, "B14a reader");
        role(jdbc, MANAGER_ID, true, true, "SELF");
        role(jdbc, READER_ID, false, false, "SELF");
        seedContract(jdbc, MANAGER_ID, "30.30");

        DashboardOverviewResponse hiddenTarget = overview(READER_ID);
        assertEquals("UNAVAILABLE", hiddenTarget.getTarget().getStatus());
        assertNull(hiddenTarget.getTarget().getTargetAmount());

        mockMvc.perform(as(READER_ID, post("/api/v1/dashboard/performance-targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(createRequest()))))
                .andExpect(status().isForbidden());

        PerformanceTargetResponse created = fromJson(mockMvc.perform(as(MANAGER_ID,
                        post("/api/v1/dashboard/performance-targets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(createRequest()))))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), PerformanceTargetResponse.class);
        assertEquals(MANAGER_ID, created.getMemberId());
        assertEquals("ACTIVE_CONTRACT", created.getMetric());
        assertEquals(2026, created.getTargetYear());
        assertEquals(7, created.getTargetMonth());
        assertEquals(new BigDecimal("100.00"), created.getAmount());
        assertEquals(0L, created.getVersion());

        PerformanceTargetListResponse queried = fromJson(mockMvc.perform(as(MANAGER_ID,
                        get("/api/v1/dashboard/performance-targets")
                                .param("memberId", Long.toString(MANAGER_ID))
                                .param("targetYear", "2026")))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), PerformanceTargetListResponse.class);
        assertEquals(1, queried.getTargets().size());
        assertEquals(created.getId(), queried.getTargets().getFirst().getId());

        DashboardOverviewResponse available = overview(MANAGER_ID);
        assertEquals("AVAILABLE", available.getTarget().getStatus());
        assertEquals(new BigDecimal("100.00"), available.getTarget().getTargetAmount());
        assertEquals(new BigDecimal("30.30"), available.getTarget().getActualAmount());
        assertEquals(new BigDecimal("30.30"), available.getTarget().getCompletionRate());

        MockHttpServletRequestBuilder updateRequest = as(MANAGER_ID,
                put("/api/v1/dashboard/performance-targets/{targetId}", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new PerformanceTargetUpdateRequest()
                                .setAmount(new BigDecimal("60.00"))
                                .setVersion(created.getVersion()))));
        PerformanceTargetResponse updated = fromJson(mockMvc.perform(updateRequest)
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), PerformanceTargetResponse.class);
        assertEquals(new BigDecimal("60.00"), updated.getAmount());
        assertEquals(1L, updated.getVersion());
        MockHttpServletRequestBuilder staleUpdateRequest = as(MANAGER_ID,
                put("/api/v1/dashboard/performance-targets/{targetId}", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new PerformanceTargetUpdateRequest()
                                .setAmount(new BigDecimal("90.00"))
                                .setVersion(created.getVersion()))));
        mockMvc.perform(staleUpdateRequest)
                .andExpect(status().isConflict());

        MockHttpServletRequestBuilder deleteRequest = as(MANAGER_ID,
                delete("/api/v1/dashboard/performance-targets/{targetId}", created.getId())
                        .param("version", Long.toString(updated.getVersion())));
        mockMvc.perform(deleteRequest)
                .andExpect(status().isOk());
        DashboardOverviewResponse empty = overview(MANAGER_ID);
        assertEquals("EMPTY", empty.getTarget().getStatus());
        assertNull(empty.getTarget().getTargetAmount());
        assertNull(empty.getTarget().getActualAmount());
        assertNull(empty.getTarget().getCompletionRate());
    }

    @Test
    void importsMemberTargetRowsAtomicallyWithoutPersistingTheCsvSource() throws Exception {
        bootstrap();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long departmentId = department(jdbc, "B14e member import");
        member(jdbc, MANAGER_ID, departmentId, "B14e manager");
        member(jdbc, READER_ID, departmentId, "B14e reader");
        role(jdbc, MANAGER_ID, true, true, "SELF");
        role(jdbc, READER_ID, true, false, "SELF");
        PerformanceTargetResponse existing = fromJson(mockMvc.perform(as(MANAGER_ID,
                        post("/api/v1/dashboard/performance-targets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(createRequest()))))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), PerformanceTargetResponse.class);

        PerformanceTargetImportRequest readerImport = memberImport(
                List.of(memberImportItem(MANAGER_ID, 7, "150.00")));
        mockMvc.perform(as(READER_ID, post("/api/v1/dashboard/performance-targets/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(readerImport))))
                .andExpect(status().isForbidden());

        PerformanceTargetImportResponse imported = fromJson(mockMvc.perform(as(MANAGER_ID,
                        post("/api/v1/dashboard/performance-targets/import")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(memberImport(List.of(
                                        memberImportItem(MANAGER_ID, 7, "150.00"),
                                        memberImportItem(MANAGER_ID, 8, "200.00")))))))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), PerformanceTargetImportResponse.class);
        assertEquals(2, imported.importedCount());

        PerformanceTargetListResponse afterImport = fromJson(mockMvc.perform(as(MANAGER_ID,
                        get("/api/v1/dashboard/performance-targets")
                                .param("memberId", Long.toString(MANAGER_ID))
                                .param("targetYear", "2026")))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), PerformanceTargetListResponse.class);
        assertEquals(2, afterImport.getTargets().size());
        assertEquals(new BigDecimal("150.00"), afterImport.getTargets().getFirst().getAmount());
        assertEquals(existing.getVersion() + 1, afterImport.getTargets().getFirst().getVersion());

        PerformanceTargetImportRequest rejectedImport = memberImport(List.of(
                memberImportItem(MANAGER_ID, 9, "300.00"),
                memberImportItem(9_999_999L, 10, "400.00")));
        mockMvc.perform(as(MANAGER_ID, post("/api/v1/dashboard/performance-targets/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(rejectedImport))))
                .andExpect(status().isNotFound());
        PerformanceTargetListResponse afterRejectedImport = fromJson(mockMvc.perform(as(MANAGER_ID,
                        get("/api/v1/dashboard/performance-targets")
                                .param("memberId", Long.toString(MANAGER_ID))
                                .param("targetYear", "2026")))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), PerformanceTargetListResponse.class);
        assertEquals(2, afterRejectedImport.getTargets().size());
    }

    private PerformanceTargetCreateRequest createRequest() {
        return new PerformanceTargetCreateRequest()
                .setMemberId(MANAGER_ID)
                .setMetric("ACTIVE_CONTRACT")
                .setTargetYear(2026)
                .setTargetMonth(7)
                .setAmount(new BigDecimal("100.00"));
    }

    private PerformanceTargetImportRequest memberImport(List<PerformanceTargetImportItemRequest> targets) {
        return new PerformanceTargetImportRequest().setMetric("ACTIVE_CONTRACT").setTargets(targets);
    }

    private PerformanceTargetImportItemRequest memberImportItem(long memberId, int targetMonth, String amount) {
        return new PerformanceTargetImportItemRequest()
                .setMemberId(memberId)
                .setTargetYear(2026)
                .setTargetMonth(targetMonth)
                .setAmount(new BigDecimal(amount));
    }

    private DashboardOverviewResponse overview(long actorId) throws Exception {
        return fromJson(mockMvc.perform(as(actorId, get("/api/v1/dashboard/overview")))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(),
                DashboardOverviewResponse.class);
    }

    private void bootstrap() throws Exception {
        mockMvc.perform(post("/api/v1/organization/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OrganizationBootstrapRequest()
                                .setDisplayName("B14a owner")))
                        .header("X-Dev-User-Id", OWNER_ID)
                        .header("X-Dev-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk());
    }

    private long department(JdbcTemplate jdbc, String name) {
        jdbc.update("INSERT INTO organization_department "
                        + "(tenant_id, parent_department_id, name, status) VALUES (?, 0, ?, 'ACTIVE')",
                TENANT_ID, name);
        return jdbc.queryForObject("SELECT id FROM organization_department "
                        + "WHERE tenant_id = ? AND name = ?", Long.class, TENANT_ID, name);
    }

    private void member(JdbcTemplate jdbc, long actorId, long departmentId, String displayName) {
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                actorId, displayName);
        jdbc.update("INSERT INTO organization_membership "
                        + "(tenant_id, actor_id, department_id, status) VALUES (?, ?, ?, 'ACTIVE')",
                TENANT_ID, actorId, departmentId);
    }

    private void role(
            JdbcTemplate jdbc, long actorId, boolean targetRead, boolean targetWrite, String dataScope) {
        String name = "B14a " + actorId;
        jdbc.update("INSERT INTO authorization_role (tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, name);
        long roleId = jdbc.queryForObject("SELECT id FROM authorization_role "
                        + "WHERE tenant_id = ? AND name = ?", Long.class, TENANT_ID, name);
        grant(jdbc, roleId, "reports:read", null);
        grant(jdbc, roleId, "contracts:read", dataScope);
        if (targetRead) {
            grant(jdbc, roleId, "dashboard-targets:read", null);
        }
        if (targetWrite) {
            grant(jdbc, roleId, "dashboard-targets:write", null);
        }
        jdbc.update("INSERT INTO role_field_policy "
                        + "(tenant_id, role_id, object_type) VALUES (?, ?, 'CONTRACT')",
                TENANT_ID, roleId);
        jdbc.update("INSERT INTO role_field_access "
                        + "(tenant_id, role_id, object_type, field_id, access_level, mask_type) "
                        + "SELECT ?, ?, 'CONTRACT', id, 'READ', 'NONE' "
                        + "FROM custom_field_definition WHERE tenant_id = ? "
                        + "AND object_type = 'CONTRACT' AND is_builtin = 1",
                TENANT_ID, roleId, TENANT_ID);
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT_ID, actorId, roleId);
    }

    private void grant(JdbcTemplate jdbc, long roleId, String authority, String dataScope) {
        jdbc.update("INSERT INTO authorization_role_grant "
                        + "(tenant_id, role_id, authority_code, data_scope) VALUES (?, ?, ?, ?)",
                TENANT_ID, roleId, authority, dataScope);
    }

    private void seedContract(JdbcTemplate jdbc, long ownerActorId, String amount) {
        jdbc.update("INSERT INTO crm_customer "
                        + "(tenant_id, owner_actor_id, name, status, deal_status) "
                        + "VALUES (?, ?, 'B14a customer', 'ACTIVE', 'OPEN')", TENANT_ID, ownerActorId);
        long customerId = jdbc.queryForObject("SELECT id FROM crm_customer "
                        + "WHERE tenant_id = ? AND name = 'B14a customer'", Long.class, TENANT_ID);
        jdbc.update("INSERT INTO crm_contract "
                        + "(tenant_id, owner_actor_id, customer_id, name, contract_number, "
                        + "contract_number_key, order_date, subtotal, discount_rate, total_amount, status) "
                        + "VALUES (?, ?, ?, 'B14a contract', 'B14A-001', 'b14a-001', ?, ?, 0.0000, ?, 'ACTIVE')",
                TENANT_ID, ownerActorId, customerId, AS_OF_DATE,
                new BigDecimal(amount), new BigDecimal(amount));
    }

    private MockHttpServletRequestBuilder as(long actorId, MockHttpServletRequestBuilder request) {
        return request.header("X-Dev-User-Id", actorId)
                .header("X-Dev-Tenant-Id", TENANT_ID);
    }

    @Override
    protected void initTestData() {
        clearOwnTenant();
    }

    @Override
    protected void clearTestData() {
        clearOwnTenant();
    }

    private void clearOwnTenant() {
        executeSql("DELETE FROM dashboard_performance_target WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_contract WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_customer WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM dashboard_preference WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM role_field_access WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM role_field_policy WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM authorization_role_assignment WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM authorization_role_grant WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM authorization_role WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM custom_field_definition WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM customization_object_guard WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM organization_membership WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM organization_department WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM organization_tenant WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM organization_member_profile WHERE actor_id BETWEEN "
                + OWNER_ID + " AND " + READER_ID);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedDashboardClock() {
            return Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneOffset.UTC);
        }
    }
}
