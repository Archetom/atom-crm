package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetCreateRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetBatchItemRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetBatchUpsertRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetImportItemRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetImportRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetUpdateRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.response.DepartmentPerformanceTargetListResponse;
import io.github.archetom.crm.api.dto.response.DepartmentPerformanceTargetResponse;
import io.github.archetom.crm.api.dto.response.PerformanceTargetImportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MySQL and HTTP coverage for department year, quarter and month target settings. */
@RequiresDatabase
class DepartmentPerformanceTargetIntegrationTest extends BaseIntegrationTest {

    private static final long TENANT_ID = 1_718_510L;
    private static final long OWNER_ID = 1_725_011L;
    private static final long MANAGER_ID = 1_725_012L;
    private static final long READER_ID = 1_725_013L;

    @Test
    void managesVersionedDepartmentTargetsAcrossAllSupportedCalendarPeriods() throws Exception {
        bootstrap();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long departmentId = department(jdbc, "B14c Sales");
        member(jdbc, MANAGER_ID, departmentId, "B14c manager");
        member(jdbc, READER_ID, departmentId, "B14c reader");
        role(jdbc, MANAGER_ID, true, true);
        role(jdbc, READER_ID, true, false);

        mockMvc.perform(as(READER_ID, post("/api/v1/dashboard/department-performance-targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request(departmentId, "YEAR", 1, "1200.00")))))
                .andExpect(status().isForbidden());

        DepartmentPerformanceTargetResponse yearly = create(departmentId, "YEAR", 1, "1200.00");
        DepartmentPerformanceTargetResponse quarterly = create(departmentId, "QUARTER", 3, "360.00");
        DepartmentPerformanceTargetResponse monthly = create(departmentId, "MONTH", 8, "120.00");
        assertEquals("YEAR", yearly.getPeriod());
        assertEquals("QUARTER", quarterly.getPeriod());
        assertEquals("MONTH", monthly.getPeriod());

        DepartmentPerformanceTargetListResponse queried = fromJson(mockMvc.perform(as(READER_ID,
                        get("/api/v1/dashboard/department-performance-targets")
                                .param("departmentId", Long.toString(departmentId))
                                .param("targetYear", "2026")))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), DepartmentPerformanceTargetListResponse.class);
        assertEquals(3, queried.getTargets().size());

        DepartmentPerformanceTargetResponse updated = fromJson(mockMvc.perform(as(MANAGER_ID,
                        put("/api/v1/dashboard/department-performance-targets/{targetId}", quarterly.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new DepartmentPerformanceTargetUpdateRequest()
                                        .setAmount(new BigDecimal("400.00"))
                                        .setVersion(quarterly.getVersion())))))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), DepartmentPerformanceTargetResponse.class);
        assertEquals(new BigDecimal("400.00"), updated.getAmount());
        assertEquals(1L, updated.getVersion());

        mockMvc.perform(as(MANAGER_ID,
                        put("/api/v1/dashboard/department-performance-targets/{targetId}", quarterly.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new DepartmentPerformanceTargetUpdateRequest()
                                        .setAmount(new BigDecimal("410.00"))
                                        .setVersion(quarterly.getVersion())))))
                .andExpect(status().isConflict());

        mockMvc.perform(as(MANAGER_ID,
                        post("/api/v1/dashboard/department-performance-targets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(request(departmentId, "QUARTER", 5, "100.00")))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(as(MANAGER_ID,
                        delete("/api/v1/dashboard/department-performance-targets/{targetId}", monthly.getId())
                                .param("version", Long.toString(monthly.getVersion()))))
                .andExpect(status().isOk());
    }

    @Test
    void batchSetsExplicitDepartmentPeriodsAtomicallyAgainstTheirVersions() throws Exception {
        bootstrap();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long departmentId = department(jdbc, "B14d Sales");
        member(jdbc, MANAGER_ID, departmentId, "B14d manager");
        member(jdbc, READER_ID, departmentId, "B14d reader");
        role(jdbc, MANAGER_ID, true, true);
        role(jdbc, READER_ID, true, false);
        DepartmentPerformanceTargetResponse quarter = create(departmentId, "QUARTER", 2, "300.00");

        mockMvc.perform(as(READER_ID, put("/api/v1/dashboard/department-performance-targets/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(batchRequest(departmentId, List.of(
                                batchItem("YEAR", 1, "1200.00", null)))))))
                .andExpect(status().isForbidden());

        DepartmentPerformanceTargetListResponse applied = fromJson(mockMvc.perform(as(MANAGER_ID,
                        put("/api/v1/dashboard/department-performance-targets/batch")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(batchRequest(departmentId, List.of(
                                        batchItem("YEAR", 1, "1200.00", null),
                                        batchItem("QUARTER", 2, "360.00", quarter.getVersion())))))))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), DepartmentPerformanceTargetListResponse.class);
        assertEquals(2, applied.getTargets().size());
        assertEquals(new BigDecimal("1200.00"), applied.getTargets().get(0).getAmount());
        assertEquals(new BigDecimal("360.00"), applied.getTargets().get(1).getAmount());
        assertEquals(1L, applied.getTargets().get(1).getVersion());

        mockMvc.perform(as(MANAGER_ID,
                        put("/api/v1/dashboard/department-performance-targets/batch")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(batchRequest(departmentId, List.of(
                                        batchItem("MONTH", 5, "100.00", null),
                                        batchItem("QUARTER", 2, "400.00", quarter.getVersion())))))))
                .andExpect(status().isConflict());

        DepartmentPerformanceTargetListResponse afterRejectedBatch = fromJson(mockMvc.perform(as(READER_ID,
                        get("/api/v1/dashboard/department-performance-targets")
                                .param("departmentId", Long.toString(departmentId))
                                .param("targetYear", "2026")))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), DepartmentPerformanceTargetListResponse.class);
        assertEquals(2, afterRejectedBatch.getTargets().size());
    }

    @Test
    void importsDepartmentTargetRowsAtomicallyWithoutPersistingTheCsvSource() throws Exception {
        bootstrap();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long departmentId = department(jdbc, "B14e department import");
        member(jdbc, MANAGER_ID, departmentId, "B14e manager");
        member(jdbc, READER_ID, departmentId, "B14e reader");
        role(jdbc, MANAGER_ID, true, true);
        role(jdbc, READER_ID, true, false);
        DepartmentPerformanceTargetResponse existing = create(departmentId, "QUARTER", 2, "300.00");

        DepartmentPerformanceTargetImportRequest readerImport = departmentImport(
                List.of(departmentImportItem(departmentId, "QUARTER", 2, "360.00")));
        mockMvc.perform(as(READER_ID, post("/api/v1/dashboard/department-performance-targets/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(readerImport))))
                .andExpect(status().isForbidden());

        PerformanceTargetImportResponse imported = fromJson(mockMvc.perform(as(MANAGER_ID,
                        post("/api/v1/dashboard/department-performance-targets/import")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(departmentImport(List.of(
                                        departmentImportItem(departmentId, "QUARTER", 2, "360.00"),
                                        departmentImportItem(departmentId, "MONTH", 8, "120.00")))))))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), PerformanceTargetImportResponse.class);
        assertEquals(2, imported.importedCount());

        DepartmentPerformanceTargetListResponse afterImport = targets(departmentId);
        assertEquals(2, afterImport.getTargets().size());
        DepartmentPerformanceTargetResponse updatedQuarter = afterImport.getTargets().stream()
                .filter(target -> "QUARTER".equals(target.getPeriod()) && target.getPeriodNumber() == 2)
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("360.00"), updatedQuarter.getAmount());
        assertEquals(existing.getVersion() + 1, updatedQuarter.getVersion());

        DepartmentPerformanceTargetImportRequest rejectedImport = departmentImport(List.of(
                departmentImportItem(departmentId, "MONTH", 9, "140.00"),
                departmentImportItem(9_999_999L, "MONTH", 10, "150.00")));
        mockMvc.perform(as(MANAGER_ID, post("/api/v1/dashboard/department-performance-targets/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(rejectedImport))))
                .andExpect(status().isNotFound());
        assertEquals(2, targets(departmentId).getTargets().size());
    }

    private DepartmentPerformanceTargetResponse create(
            long departmentId, String period, int periodNumber, String amount) throws Exception {
        return fromJson(mockMvc.perform(as(MANAGER_ID,
                        post("/api/v1/dashboard/department-performance-targets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(request(departmentId, period, periodNumber, amount)))))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), DepartmentPerformanceTargetResponse.class);
    }

    private DepartmentPerformanceTargetCreateRequest request(
            long departmentId, String period, int periodNumber, String amount) {
        return new DepartmentPerformanceTargetCreateRequest()
                .setDepartmentId(departmentId)
                .setMetric("ACTIVE_CONTRACT")
                .setTargetYear(2026)
                .setPeriod(period)
                .setPeriodNumber(periodNumber)
                .setAmount(new BigDecimal(amount));
    }

    private DepartmentPerformanceTargetBatchUpsertRequest batchRequest(
            long departmentId, List<DepartmentPerformanceTargetBatchItemRequest> targets) {
        return new DepartmentPerformanceTargetBatchUpsertRequest()
                .setDepartmentId(departmentId)
                .setMetric("ACTIVE_CONTRACT")
                .setTargetYear(2026)
                .setTargets(targets);
    }

    private DepartmentPerformanceTargetBatchItemRequest batchItem(
            String period, int periodNumber, String amount, Long version) {
        return new DepartmentPerformanceTargetBatchItemRequest()
                .setPeriod(period)
                .setPeriodNumber(periodNumber)
                .setAmount(new BigDecimal(amount))
                .setVersion(version);
    }

    private DepartmentPerformanceTargetImportRequest departmentImport(
            List<DepartmentPerformanceTargetImportItemRequest> targets) {
        return new DepartmentPerformanceTargetImportRequest()
                .setMetric("ACTIVE_CONTRACT")
                .setTargets(targets);
    }

    private DepartmentPerformanceTargetImportItemRequest departmentImportItem(
            long departmentId, String period, int periodNumber, String amount) {
        return new DepartmentPerformanceTargetImportItemRequest()
                .setDepartmentId(departmentId)
                .setTargetYear(2026)
                .setPeriod(period)
                .setPeriodNumber(periodNumber)
                .setAmount(new BigDecimal(amount));
    }

    private DepartmentPerformanceTargetListResponse targets(long departmentId) throws Exception {
        return fromJson(mockMvc.perform(as(READER_ID,
                        get("/api/v1/dashboard/department-performance-targets")
                                .param("departmentId", Long.toString(departmentId))
                                .param("targetYear", "2026")))
                        .andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString(), DepartmentPerformanceTargetListResponse.class);
    }

    private void bootstrap() throws Exception {
        mockMvc.perform(post("/api/v1/organization/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OrganizationBootstrapRequest().setDisplayName("B14c owner")))
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

    private void role(JdbcTemplate jdbc, long actorId, boolean targetRead, boolean targetWrite) {
        String name = "B14c " + actorId;
        jdbc.update("INSERT INTO authorization_role (tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, name);
        long roleId = jdbc.queryForObject("SELECT id FROM authorization_role "
                        + "WHERE tenant_id = ? AND name = ?", Long.class, TENANT_ID, name);
        if (targetRead) {
            grant(jdbc, roleId, "dashboard-targets:read");
        }
        if (targetWrite) {
            grant(jdbc, roleId, "dashboard-targets:write");
        }
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT_ID, actorId, roleId);
    }

    private void grant(JdbcTemplate jdbc, long roleId, String authority) {
        jdbc.update("INSERT INTO authorization_role_grant "
                        + "(tenant_id, role_id, authority_code, data_scope) VALUES (?, ?, ?, NULL)",
                TENANT_ID, roleId, authority);
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
        executeSql("DELETE FROM dashboard_department_performance_target WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM dashboard_performance_target WHERE tenant_id = " + TENANT_ID);
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
}
