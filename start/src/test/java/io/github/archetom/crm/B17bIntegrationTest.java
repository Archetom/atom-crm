package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.ReportRankingRequest;
import io.github.archetom.crm.api.dto.request.ReportTrendRequest;
import io.github.archetom.crm.api.dto.response.ReportRankingResponse;
import io.github.archetom.crm.api.dto.response.ReportTrendResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B17b fixed trend and ranking contract against production MySQL predicates. */
@RequiresDatabase
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class B17bIntegrationTest extends BaseIntegrationTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_3 = LocalDate.of(2026, 1, 3);
    private static final LocalDate JAN_4 = LocalDate.of(2026, 1, 4);
    private static final LocalDate JAN_6 = LocalDate.of(2026, 1, 6);
    private static final LocalDate JAN_12 = LocalDate.of(2026, 1, 12);
    private static final LocalDate JAN_14 = LocalDate.of(2026, 1, 14);
    private static final LocalDate FEB_16 = LocalDate.of(2026, 2, 16);
    private static final LocalDate FEB_23 = LocalDate.of(2026, 2, 23);
    private static final LocalDate FEB_27 = LocalDate.of(2026, 2, 27);
    private static final LocalDate FEB_28 = LocalDate.of(2026, 2, 28);
    private static final LocalDate MAR_1 = LocalDate.of(2026, 3, 1);
    private static final LocalDate MAR_31 = LocalDate.of(2026, 3, 31);

    private Scenario trendMatrixScenario;

    @ParameterizedTest(name = "{0}")
    @MethodSource("trendCases")
    void everyMetricAndGranularityUsesItsMySqlBucketProjection(TrendCase trendCase)
            throws Exception {
        Scenario scenario = trendMatrixScenario();
        ReportTrendResponse response = trend(scenario.tenantReader(),
                trendCase.metric(), trendCase.from(), trendCase.to(),
                trendCase.granularity());

        assertEquals(trendCase.metric(), response.getMetric());
        assertEquals(trendCase.from(), response.getFrom());
        assertEquals(trendCase.to(), response.getTo());
        assertEquals(trendCase.granularity(), response.getGranularity());
        assertEquals(trendCase.points().size(), response.getPoints().size());
        for (int index = 0; index < trendCase.points().size(); index++) {
            ExpectedPoint point = trendCase.points().get(index);
            assertPoint(response, index, point.periodStart(), point.count(), point.amount());
        }
    }

    @Test
    void rankingsStayStableForEveryMetricAndUseTheDefaultLimit() throws Exception {
        Scenario scenario = trendMatrixScenario();
        assertStableRanking(scenario, "WON_OPPORTUNITY", JAN_1, JAN_3);
        assertStableRanking(scenario, "ACTIVE_CONTRACT", JAN_1, JAN_14);
        assertStableRanking(scenario, "ACTIVE_RECEIVABLE", JAN_1, MAR_31);

        ReportRankingResponse defaultLimit = fromJson(mockMvc.perform(as(
                        scenario.tenantReader(), post("/api/v1/reports/rankings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(Map.of("metric", "WON_OPPORTUNITY",
                                        "from", JAN_1, "to", JAN_3)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReportRankingResponse.class);
        assertEquals(4, defaultLimit.getItems().size());

        assertReportDateIndexes(new JdbcTemplate(dataSource));
    }

    /** The trend matrix is seeded once and reused by every parameterised case in this class. */
    @Override
    protected boolean resetsBetweenTests() {
        return false;
    }

    private synchronized Scenario trendMatrixScenario() throws Exception {
        if (trendMatrixScenario == null) {
            trendMatrixScenario = scenario(1_717_100L, 1_717_100L);
            seedCrossTenant(1_717_101L, 1_718_100L);
        }
        return trendMatrixScenario;
    }

    private static Stream<TrendCase> trendCases() {
        return Stream.of(
                new TrendCase("WON_OPPORTUNITY", JAN_1, JAN_4, "DAY", List.of(
                        point(JAN_1, 2L, "30.30"),
                        point(LocalDate.of(2026, 1, 2), 0L, "0.00"),
                        point(JAN_3, 2L, "80.80"),
                        point(JAN_4, 1L, "500.00"))),
                new TrendCase("WON_OPPORTUNITY", JAN_1, JAN_12, "WEEK", List.of(
                        point(LocalDate.of(2025, 12, 29), 5L, "611.10"),
                        point(LocalDate.of(2026, 1, 5), 0L, "0.00"),
                        point(JAN_12, 0L, "0.00"))),
                new TrendCase("WON_OPPORTUNITY", JAN_1, MAR_1, "MONTH", List.of(
                        point(JAN_1, 5L, "611.10"),
                        point(LocalDate.of(2026, 2, 1), 0L, "0.00"),
                        point(MAR_1, 0L, "0.00"))),
                new TrendCase("ACTIVE_CONTRACT", JAN_1, JAN_6, "DAY", List.of(
                        point(JAN_1, 2L, "30.30"),
                        point(LocalDate.of(2026, 1, 2), 0L, "0.00"),
                        point(JAN_3, 0L, "0.00"),
                        point(JAN_4, 0L, "0.00"),
                        point(LocalDate.of(2026, 1, 5), 0L, "0.00"),
                        point(JAN_6, 2L, "80.80"))),
                new TrendCase("ACTIVE_CONTRACT", JAN_1, JAN_12, "WEEK", List.of(
                        point(LocalDate.of(2025, 12, 29), 2L, "30.30"),
                        point(LocalDate.of(2026, 1, 5), 2L, "80.80"),
                        point(JAN_12, 0L, "0.00"))),
                new TrendCase("ACTIVE_CONTRACT", JAN_1, MAR_1, "MONTH", List.of(
                        point(JAN_1, 4L, "111.10"),
                        point(LocalDate.of(2026, 2, 1), 0L, "0.00"),
                        point(MAR_1, 0L, "0.00"))),
                new TrendCase("ACTIVE_RECEIVABLE", FEB_27, MAR_1, "DAY", List.of(
                        point(FEB_27, 0L, "0.00"),
                        point(FEB_28, 0L, "0.00"),
                        point(MAR_1, 2L, "80.80"))),
                new TrendCase("ACTIVE_RECEIVABLE", FEB_16, MAR_1, "WEEK", List.of(
                        point(FEB_16, 0L, "0.00"),
                        point(FEB_23, 2L, "80.80"))),
                new TrendCase("ACTIVE_RECEIVABLE", JAN_1, MAR_1, "MONTH", List.of(
                        point(JAN_1, 2L, "30.30"),
                        point(LocalDate.of(2026, 2, 1), 0L, "0.00"),
                        point(MAR_1, 2L, "80.80"))));
    }

    private static ExpectedPoint point(LocalDate periodStart, long count, String amount) {
        return new ExpectedPoint(periodStart, count, amount);
    }

    @Test
    void eachSourceKeepsSelfDepartmentSubtreeTenantAndCollaboratorVisibility()
            throws Exception {
        Scenario scenario = scenario(1_717_200L, 1_719_100L);

        assertTotal(scenario.selfReader(), "WON_OPPORTUNITY",
                JAN_1, JAN_3, "DAY", 2L, "50.50");
        assertTotal(scenario.departmentReader(), "WON_OPPORTUNITY",
                JAN_1, JAN_3, "DAY", 2L, "30.30");
        assertTotal(scenario.subtreeReader(), "WON_OPPORTUNITY",
                JAN_1, JAN_3, "DAY", 3L, "70.70");
        assertTotal(scenario.tenantReader(), "WON_OPPORTUNITY",
                JAN_1, JAN_3, "DAY", 4L, "111.10");

        assertTotal(scenario.selfReader(), "ACTIVE_CONTRACT",
                JAN_1, JAN_14, "WEEK", 2L, "50.50");
        assertTotal(scenario.departmentReader(), "ACTIVE_CONTRACT",
                JAN_1, JAN_14, "WEEK", 2L, "30.30");
        assertTotal(scenario.subtreeReader(), "ACTIVE_CONTRACT",
                JAN_1, JAN_14, "WEEK", 3L, "70.70");
        assertTotal(scenario.tenantReader(), "ACTIVE_CONTRACT",
                JAN_1, JAN_14, "WEEK", 4L, "111.10");

        assertTotal(scenario.selfReader(), "ACTIVE_RECEIVABLE",
                JAN_1, MAR_31, "MONTH", 1L, "10.10");
        assertTotal(scenario.departmentReader(), "ACTIVE_RECEIVABLE",
                JAN_1, MAR_31, "MONTH", 2L, "30.30");
        assertTotal(scenario.subtreeReader(), "ACTIVE_RECEIVABLE",
                JAN_1, MAR_31, "MONTH", 3L, "70.70");
        assertTotal(scenario.tenantReader(), "ACTIVE_RECEIVABLE",
                JAN_1, MAR_31, "MONTH", 4L, "111.10");
    }

    @Test
    void selectedSourceCapabilitiesFieldsMembershipAndBoundsFailClosed() throws Exception {
        Scenario scenario = scenario(1_717_300L, 1_720_100L);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        trend(scenario.opportunityOnlyReader(),
                "WON_OPPORTUNITY", JAN_1, JAN_3, "DAY");
        expectForbidden(scenario.opportunityOnlyReader(), trendRequest(
                "ACTIVE_CONTRACT", JAN_1, JAN_14, "WEEK"), "/trends");
        expectForbidden(scenario.opportunityOnlyReader(), trendRequest(
                "ACTIVE_RECEIVABLE", JAN_1, MAR_31, "MONTH"), "/trends");
        expectForbidden(scenario.sourceOnlyReader(), trendRequest(
                "WON_OPPORTUNITY", JAN_1, JAN_3, "DAY"), "/trends");

        hideField(jdbc, scenario.tenantRoleId(), "OPPORTUNITY", "owner_actor");
        trend(scenario.tenantReader(), "WON_OPPORTUNITY", JAN_1, JAN_3, "DAY");
        expectForbidden(scenario.tenantReader(), rankingRequest(
                "WON_OPPORTUNITY", JAN_1, JAN_3, 10), "/rankings");
        restoreField(jdbc, scenario.tenantRoleId(), "OPPORTUNITY", "owner_actor");

        hideField(jdbc, scenario.tenantRoleId(), "CONTRACT", "order_date");
        expectForbidden(scenario.tenantReader(), trendRequest(
                "ACTIVE_CONTRACT", JAN_1, JAN_14, "WEEK"), "/trends");
        restoreField(jdbc, scenario.tenantRoleId(), "CONTRACT", "order_date");

        maskField(jdbc, scenario.tenantRoleId(), "RECEIVABLE", "amount", "FULL");
        expectForbidden(scenario.tenantReader(), trendRequest(
                "ACTIVE_RECEIVABLE", JAN_1, MAR_31, "MONTH"), "/trends");
        maskField(jdbc, scenario.tenantRoleId(), "RECEIVABLE", "amount", "NONE");

        expectBadRequest(scenario.tenantReader(), trendRequest(
                "WON_OPPORTUNITY", JAN_1, LocalDate.of(2026, 5, 1), "DAY"),
                "/trends");
        expectBadRequest(scenario.tenantReader(), trendRequest(
                "WON_OPPORTUNITY", JAN_1, JAN_3, "QUARTER"), "/trends");
        expectBadRequest(scenario.tenantReader(), rankingRequest(
                "ACTIVE_CONTRACT", JAN_1, LocalDate.of(2027, 1, 2), 10),
                "/rankings");
        expectBadRequest(scenario.tenantReader(), rankingRequest(
                "ACTIVE_CONTRACT", JAN_1, JAN_14, 101), "/rankings");

        jdbc.update("UPDATE organization_membership SET status = 'SUSPENDED' "
                        + "WHERE tenant_id = ? AND actor_id = ?",
                scenario.tenantId(), scenario.opportunityOnlyReader());
        expectForbidden(scenario.opportunityOnlyReader(), trendRequest(
                "WON_OPPORTUNITY", JAN_1, JAN_3, "DAY"), "/trends");
    }

    private Scenario scenario(long tenantId, long actorBase) throws Exception {
        long owner = actorBase + 1;
        long self = actorBase + 2;
        long department = actorBase + 3;
        long subtree = actorBase + 4;
        long tenant = actorBase + 5;
        long peer = actorBase + 6;
        long childOwner = actorBase + 7;
        long westOwner = actorBase + 8;
        long opportunityOnly = actorBase + 9;
        long sourceOnly = actorBase + 10;
        bootstrap(tenantId, owner);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long root = department(jdbc, tenantId, 0L, "Sales");
        long child = department(jdbc, tenantId, root, "East");
        long west = department(jdbc, tenantId, 0L, "West");
        member(jdbc, tenantId, self, root, "Self reader");
        member(jdbc, tenantId, department, root, "Department reader");
        member(jdbc, tenantId, subtree, root, "Subtree reader");
        member(jdbc, tenantId, tenant, root, "Tenant reader");
        member(jdbc, tenantId, peer, root, "Peer owner");
        member(jdbc, tenantId, childOwner, child, "Child owner");
        member(jdbc, tenantId, westOwner, west, "West owner");
        member(jdbc, tenantId, opportunityOnly, root, "Opportunity-only reader");
        member(jdbc, tenantId, sourceOnly, root, "Source-only reader");
        seedBusinessRows(jdbc, tenantId, self, peer, childOwner, westOwner);

        role(jdbc, tenantId, self, "SELF", true, List.of(
                "OPPORTUNITY", "CONTRACT", "RECEIVABLE"));
        role(jdbc, tenantId, department, "DEPARTMENT", true, List.of(
                "OPPORTUNITY", "CONTRACT", "RECEIVABLE"));
        role(jdbc, tenantId, subtree, "DEPARTMENT_SUBTREE", true, List.of(
                "OPPORTUNITY", "CONTRACT", "RECEIVABLE"));
        long tenantRole = role(jdbc, tenantId, tenant, "TENANT", true, List.of(
                "OPPORTUNITY", "CONTRACT", "RECEIVABLE"));
        role(jdbc, tenantId, opportunityOnly, "TENANT", true, List.of("OPPORTUNITY"));
        role(jdbc, tenantId, sourceOnly, "TENANT", false, List.of("OPPORTUNITY"));
        return new Scenario(tenantId, self, department, subtree, tenant,
                childOwner, westOwner, opportunityOnly, sourceOnly, tenantRole);
    }

    private void seedBusinessRows(
            JdbcTemplate jdbc, long tenantId,
            long self, long peer, long child, long west) {
        jdbc.update("INSERT INTO crm_customer "
                        + "(tenant_id, owner_actor_id, name, status, deal_status) "
                        + "VALUES (?, ?, 'B17b customer', 'ACTIVE', 'OPEN')",
                tenantId, self);
        long customerId = id(jdbc, tenantId, "crm_customer", "name", "B17b customer");

        opportunity(jdbc, tenantId, self, customerId,
                "self", "2026-01-01 00:00:00.000000", "10.10", "WON", "ACTIVE");
        opportunity(jdbc, tenantId, peer, customerId,
                "peer", "2026-01-01 12:00:00.000000", "20.20", "WON", "ACTIVE");
        opportunity(jdbc, tenantId, child, customerId,
                "child", "2026-01-03 10:00:00.000000", "40.40", "WON", "ACTIVE");
        long westOpportunity = opportunity(jdbc, tenantId, west, customerId,
                "west", "2026-01-03 23:59:59.999999", "40.40", "WON", "ACTIVE");
        opportunity(jdbc, tenantId, self, customerId,
                "outside-range", "2026-01-04 00:00:00.000000", "500.00", "WON", "ACTIVE");
        opportunity(jdbc, tenantId, self, customerId,
                "lost", "2026-01-02 10:00:00.000000", "600.00", "LOST", "ACTIVE");
        opportunity(jdbc, tenantId, self, customerId,
                "invalid", "2026-01-02 10:00:00.000000", "700.00", "INVALID", "ACTIVE");
        opportunity(jdbc, tenantId, self, customerId,
                "deleted", "2026-01-02 10:00:00.000000", "800.00", "WON", "DELETED");
        jdbc.update("INSERT INTO crm_opportunity_collaborator "
                        + "(tenant_id, opportunity_id, actor_id, access_level) "
                        + "VALUES (?, ?, ?, 'READ')", tenantId, westOpportunity, self);

        long selfContract = contract(jdbc, tenantId, self, customerId,
                "self", JAN_1, "10.10", "ACTIVE");
        long peerContract = contract(jdbc, tenantId, peer, customerId,
                "peer", JAN_1, "20.20", "ACTIVE");
        long childContract = contract(jdbc, tenantId, child, customerId,
                "child", LocalDate.of(2026, 1, 6), "40.40", "ACTIVE");
        long westContract = contract(jdbc, tenantId, west, customerId,
                "west", LocalDate.of(2026, 1, 6), "40.40", "ACTIVE");
        contract(jdbc, tenantId, self, customerId,
                "no-date", null, "500.00", "ACTIVE");
        contract(jdbc, tenantId, self, customerId,
                "voided", JAN_1, "600.00", "VOIDED");
        contract(jdbc, tenantId, self, customerId,
                "deleted", JAN_1, "700.00", "DELETED");
        jdbc.update("INSERT INTO crm_contract_collaborator "
                        + "(tenant_id, contract_id, actor_id, access_level) "
                        + "VALUES (?, ?, ?, 'READ')", tenantId, westContract, self);

        receivable(jdbc, tenantId, self, customerId, selfContract,
                "self", JAN_1, "10.10", "ACTIVE");
        receivable(jdbc, tenantId, peer, customerId, peerContract,
                "peer", JAN_1, "20.20", "ACTIVE");
        receivable(jdbc, tenantId, child, customerId, childContract,
                "child", LocalDate.of(2026, 3, 1), "40.40", "ACTIVE");
        receivable(jdbc, tenantId, west, customerId, westContract,
                "west", LocalDate.of(2026, 3, 1), "40.40", "ACTIVE");
        receivable(jdbc, tenantId, self, customerId, selfContract,
                "deleted", LocalDate.of(2026, 2, 1), "5.00", "DELETED");
    }

    private void seedCrossTenant(long tenantId, long actorBase) throws Exception {
        long owner = actorBase + 1;
        bootstrap(tenantId, owner);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO crm_customer "
                        + "(tenant_id, owner_actor_id, name, status, deal_status) "
                        + "VALUES (?, ?, 'Cross tenant customer', 'ACTIVE', 'OPEN')",
                tenantId, owner);
        long customerId = id(jdbc, tenantId,
                "crm_customer", "name", "Cross tenant customer");
        opportunity(jdbc, tenantId, owner, customerId, "cross-tenant",
                "2026-01-01 12:00:00.000000", "999.99", "WON", "ACTIVE");
        long contractId = contract(jdbc, tenantId, owner, customerId,
                "cross-tenant", JAN_1, "999.99", "ACTIVE");
        receivable(jdbc, tenantId, owner, customerId, contractId,
                "cross-tenant", JAN_1, "999.99", "ACTIVE");
    }

    private void assertStableRanking(
            Scenario scenario, String metric, LocalDate from, LocalDate to) throws Exception {
        ReportRankingResponse response = ranking(
                scenario.tenantReader(), metric, from, to, 2);
        assertEquals(2, response.getItems().size());
        assertEquals(1, response.getItems().get(0).getRank());
        assertEquals(scenario.childOwner(), response.getItems().get(0).getOwnerActorId());
        assertEquals(new BigDecimal("40.40"), response.getItems().get(0).getAmount());
        assertEquals(2, response.getItems().get(1).getRank());
        assertEquals(scenario.westOwner(), response.getItems().get(1).getOwnerActorId());
    }

    private void assertTotal(
            long actorId, String metric, LocalDate from, LocalDate to,
            String granularity, long count, String amount) throws Exception {
        ReportTrendResponse response = trend(actorId, metric, from, to, granularity);
        long actualCount = response.getPoints().stream()
                .mapToLong(ReportTrendResponse.ReportTrendPointResponse::getCount).sum();
        BigDecimal actualAmount = response.getPoints().stream()
                .map(ReportTrendResponse.ReportTrendPointResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(count, actualCount);
        assertEquals(new BigDecimal(amount), actualAmount);
    }

    private void assertPoint(
            ReportTrendResponse response, int index, LocalDate period,
            long count, String amount) {
        ReportTrendResponse.ReportTrendPointResponse point = response.getPoints().get(index);
        assertEquals(period, point.getPeriodStart());
        assertEquals(count, point.getCount());
        assertEquals(new BigDecimal(amount), point.getAmount());
    }

    private void assertReportDateIndexes(JdbcTemplate jdbc) {
        assertIndexColumns(jdbc, "crm_opportunity", "idx_crm_opportunity_report_date",
                List.of("tenant_id", "status", "outcome", "outcome_time",
                        "estimated_amount", "owner_actor_id"));
        assertIndexColumns(jdbc, "crm_contract", "idx_crm_contract_report_date",
                List.of("tenant_id", "status", "order_date", "total_amount",
                        "owner_actor_id"));
        assertIndexColumns(jdbc, "crm_receivable", "idx_crm_receivable_report_date",
                List.of("tenant_id", "status", "received_date", "amount",
                        "owner_actor_id"));
    }

    private void assertIndexColumns(
            JdbcTemplate jdbc, String tableName, String indexName,
            List<String> expectedColumns) {
        List<String> actualColumns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? "
                        + "AND index_name = ? ORDER BY seq_in_index",
                String.class, tableName, indexName);
        assertEquals(expectedColumns, actualColumns,
                () -> "unexpected report index definition: " + indexName);
    }

    private ReportTrendResponse trend(
            long actorId, String metric, LocalDate from, LocalDate to,
            String granularity) throws Exception {
        String content = mockMvc.perform(as(actorId, post("/api/v1/reports/trends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(trendRequest(metric, from, to, granularity)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return fromJson(content, ReportTrendResponse.class);
    }

    private ReportRankingResponse ranking(
            long actorId, String metric, LocalDate from, LocalDate to, int limit)
            throws Exception {
        String content = mockMvc.perform(as(actorId, post("/api/v1/reports/rankings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(rankingRequest(metric, from, to, limit)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return fromJson(content, ReportRankingResponse.class);
    }

    private void expectForbidden(long actorId, Object request, String path) throws Exception {
        mockMvc.perform(as(actorId, post("/api/v1/reports" + path)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request))))
                .andExpect(status().isForbidden());
    }

    private void expectBadRequest(long actorId, Object request, String path) throws Exception {
        mockMvc.perform(as(actorId, post("/api/v1/reports" + path)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request))))
                .andExpect(status().isBadRequest());
    }

    private ReportTrendRequest trendRequest(
            String metric, LocalDate from, LocalDate to, String granularity) {
        return new ReportTrendRequest().setMetric(metric).setFrom(from).setTo(to)
                .setGranularity(granularity);
    }

    private ReportRankingRequest rankingRequest(
            String metric, LocalDate from, LocalDate to, int limit) {
        return new ReportRankingRequest().setMetric(metric).setFrom(from).setTo(to)
                .setLimit(limit);
    }

    private void bootstrap(long tenantId, long ownerId) throws Exception {
        mockMvc.perform(post("/api/v1/organization/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OrganizationBootstrapRequest()
                                .setDisplayName("B17b owner")))
                        .header("X-Dev-User-Id", ownerId)
                        .header("X-Dev-Tenant-Id", tenantId))
                .andExpect(status().isOk());
    }

    private long department(
            JdbcTemplate jdbc, long tenantId, long parentId, String name) {
        jdbc.update("INSERT INTO organization_department "
                        + "(tenant_id, parent_department_id, name, status) "
                        + "VALUES (?, ?, ?, 'ACTIVE')", tenantId, parentId, name);
        return jdbc.queryForObject("SELECT id FROM organization_department "
                        + "WHERE tenant_id = ? AND name = ?",
                Long.class, tenantId, name);
    }

    private void member(
            JdbcTemplate jdbc, long tenantId, long actorId,
            long departmentId, String displayName) {
        jdbc.update("INSERT INTO organization_member_profile "
                        + "(actor_id, display_name) VALUES (?, ?)", actorId, displayName);
        jdbc.update("INSERT INTO organization_membership "
                        + "(tenant_id, actor_id, department_id, status) "
                        + "VALUES (?, ?, ?, 'ACTIVE')", tenantId, actorId, departmentId);
    }

    private long role(
            JdbcTemplate jdbc, long tenantId, long actorId, String scope,
            boolean reports, List<String> objectTypes) {
        String name = "B17b " + actorId;
        jdbc.update("INSERT INTO authorization_role "
                        + "(tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                tenantId, name);
        long roleId = jdbc.queryForObject("SELECT id FROM authorization_role "
                        + "WHERE tenant_id = ? AND name = ?", Long.class, tenantId, name);
        if (reports) {
            grant(jdbc, tenantId, roleId, "reports:read", null);
        }
        if (objectTypes.contains("OPPORTUNITY")) {
            grant(jdbc, tenantId, roleId, "opportunities:read", scope);
            allFields(jdbc, tenantId, roleId, "OPPORTUNITY");
        }
        if (objectTypes.contains("CONTRACT")) {
            grant(jdbc, tenantId, roleId, "contracts:read", scope);
            allFields(jdbc, tenantId, roleId, "CONTRACT");
        }
        if (objectTypes.contains("RECEIVABLE")) {
            grant(jdbc, tenantId, roleId, "receivables:read", scope);
            allFields(jdbc, tenantId, roleId, "RECEIVABLE");
        }
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                tenantId, actorId, roleId);
        return roleId;
    }

    private void grant(
            JdbcTemplate jdbc, long tenantId, long roleId,
            String authority, String dataScope) {
        jdbc.update("INSERT INTO authorization_role_grant "
                        + "(tenant_id, role_id, authority_code, data_scope) "
                        + "VALUES (?, ?, ?, ?)", tenantId, roleId, authority, dataScope);
    }

    private void allFields(
            JdbcTemplate jdbc, long tenantId, long roleId, String objectType) {
        jdbc.update("INSERT INTO role_field_policy "
                        + "(tenant_id, role_id, object_type) VALUES (?, ?, ?)",
                tenantId, roleId, objectType);
        jdbc.update("INSERT INTO role_field_access "
                        + "(tenant_id, role_id, object_type, field_id, "
                        + "access_level, mask_type) "
                        + "SELECT ?, ?, ?, id, 'READ', 'NONE' "
                        + "FROM custom_field_definition WHERE tenant_id = ? "
                        + "AND object_type = ? AND is_builtin = 1",
                tenantId, roleId, objectType, tenantId, objectType);
    }

    private long opportunity(
            JdbcTemplate jdbc, long tenantId, long ownerId, long customerId,
            String name, String outcomeTime, String amount,
            String outcome, String status) {
        OpportunityPipelineFixture.provisionActivePair(jdbc, tenantId, "direct", "qualification");
        jdbc.update("INSERT INTO crm_opportunity "
                        + "(tenant_id, owner_actor_id, customer_id, name, pipeline_code, "
                        + "stage_code, estimated_amount, outcome, outcome_time, status) "
                        + "VALUES (?, ?, ?, ?, 'direct', 'qualification', ?, ?, ?, ?)",
                tenantId, ownerId, customerId, name, new BigDecimal(amount),
                outcome, outcomeTime, status);
        return id(jdbc, tenantId, "crm_opportunity", "name", name);
    }

    private long contract(
            JdbcTemplate jdbc, long tenantId, long ownerId, long customerId,
            String key, LocalDate orderDate, String amount, String status) {
        String number = "B17B-" + tenantId + "-" + key;
        jdbc.update("INSERT INTO crm_contract "
                        + "(tenant_id, owner_actor_id, customer_id, name, contract_number, "
                        + "contract_number_key, order_date, subtotal, discount_rate, "
                        + "total_amount, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0.0000, ?, ?)",
                tenantId, ownerId, customerId, key, number, number.toLowerCase(),
                orderDate, new BigDecimal(amount), new BigDecimal(amount), status);
        return id(jdbc, tenantId, "crm_contract", "contract_number", number);
    }

    private void receivable(
            JdbcTemplate jdbc, long tenantId, long ownerId, long customerId,
            long contractId, String key, LocalDate date, String amount, String status) {
        String number = "B17BR-" + tenantId + "-" + key;
        jdbc.update("INSERT INTO crm_receivable "
                        + "(tenant_id, owner_actor_id, customer_id, contract_id, "
                        + "receipt_number, received_date, payment_method, amount, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'BANK', ?, ?)",
                tenantId, ownerId, customerId, contractId, number, date,
                new BigDecimal(amount), status);
    }

    private void hideField(
            JdbcTemplate jdbc, long roleId, String objectType, String fieldCode) {
        jdbc.update("DELETE access_rule FROM role_field_access access_rule "
                        + "JOIN custom_field_definition field_definition "
                        + "ON field_definition.tenant_id = access_rule.tenant_id "
                        + "AND field_definition.id = access_rule.field_id "
                        + "WHERE access_rule.role_id = ? AND access_rule.object_type = ? "
                        + "AND field_definition.field_code = ?",
                roleId, objectType, fieldCode);
    }

    private void restoreField(
            JdbcTemplate jdbc, long roleId, String objectType, String fieldCode) {
        jdbc.update("INSERT INTO role_field_access "
                        + "(tenant_id, role_id, object_type, field_id, "
                        + "access_level, mask_type) "
                        + "SELECT field_definition.tenant_id, ?, ?, field_definition.id, "
                        + "'READ', 'NONE' FROM custom_field_definition field_definition "
                        + "JOIN authorization_role role "
                        + "ON role.tenant_id = field_definition.tenant_id "
                        + "WHERE role.id = ? AND field_definition.object_type = ? "
                        + "AND field_definition.field_code = ?",
                roleId, objectType, roleId, objectType, fieldCode);
    }

    private void maskField(
            JdbcTemplate jdbc, long roleId, String objectType,
            String fieldCode, String mask) {
        jdbc.update("UPDATE role_field_access access_rule "
                        + "JOIN custom_field_definition field_definition "
                        + "ON field_definition.tenant_id = access_rule.tenant_id "
                        + "AND field_definition.id = access_rule.field_id "
                        + "SET access_rule.mask_type = ? WHERE access_rule.role_id = ? "
                        + "AND access_rule.object_type = ? AND field_definition.field_code = ?",
                mask, roleId, objectType, fieldCode);
    }

    private long id(
            JdbcTemplate jdbc, long tenantId,
            String table, String column, String value) {
        String sql = "SELECT id FROM " + table
                + " WHERE tenant_id = ? AND " + column + " = ?";
        return jdbc.queryForObject(sql, Long.class, tenantId, value);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder as(
            long actorId,
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        long tenantId = actorTenant(actorId);
        return request.header("X-Dev-User-Id", actorId)
                .header("X-Dev-Tenant-Id", tenantId);
    }

    private long actorTenant(long actorId) {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT tenant_id FROM organization_membership WHERE actor_id = ?",
                Long.class, actorId);
    }

    private record TrendCase(
            String metric,
            LocalDate from,
            LocalDate to,
            String granularity,
            List<ExpectedPoint> points) {

        @Override
        public String toString() {
            return metric + "/" + granularity;
        }
    }

    private record ExpectedPoint(
            LocalDate periodStart, long count, String amount) {
    }

    private record Scenario(
            long tenantId,
            long selfReader,
            long departmentReader,
            long subtreeReader,
            long tenantReader,
            long childOwner,
            long westOwner,
            long opportunityOnlyReader,
            long sourceOnlyReader,
            long tenantRoleId) {
    }
}
