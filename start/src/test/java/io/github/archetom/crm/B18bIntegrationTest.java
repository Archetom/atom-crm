package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.DashboardPreferenceUpdateRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.response.DashboardOverviewResponse;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MySQL and HTTP coverage for the B18b fixed sales dashboard. */
@RequiresDatabase
@Import(B18bIntegrationTest.FixedClockConfiguration.class)
class B18bIntegrationTest extends BaseIntegrationTest {

    private static final long TENANT_ID = 1_718_400L;
    private static final long OWNER_ID = 1_724_001L;
    private static final long TENANT_READER = 1_724_002L;
    private static final long PEER_OWNER = 1_724_003L;
    private static final long WEST_OWNER = 1_724_004L;
    private static final long OPPORTUNITY_ONLY = 1_724_005L;
    private static final long SOURCE_ONLY = 1_724_006L;
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 7, 18);

    @Test
    void overviewUsesSavedNarrowingAndKeepsDeniedCardsPartiallyAvailable()
            throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/overview"))
                .andExpect(status().isUnauthorized());

        bootstrap();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long sales = department(jdbc, 0L, "B18b Sales");
        long west = department(jdbc, 0L, "B18b West");
        member(jdbc, TENANT_READER, sales, "B18b tenant reader");
        member(jdbc, PEER_OWNER, sales, "B18b peer owner");
        member(jdbc, WEST_OWNER, west, "B18b west owner");
        member(jdbc, OPPORTUNITY_ONLY, west, "B18b opportunity reader");
        member(jdbc, SOURCE_ONLY, west, "B18b source-only reader");
        long tenantRole = role(jdbc, TENANT_READER, "TENANT", true,
                List.of("CUSTOMER", "OPPORTUNITY", "CONTRACT", "RECEIVABLE", "RECEIVABLE_PLAN"));
        grant(jdbc, tenantRole, "activities:read", null);
        grant(jdbc, tenantRole, "customer-pools:read", null);
        grant(jdbc, tenantRole, "opportunity-pipelines:read", null);
        role(jdbc, OPPORTUNITY_ONLY, "TENANT", true, List.of("OPPORTUNITY"));
        role(jdbc, SOURCE_ONLY, "TENANT", false, List.of("OPPORTUNITY"));
        seedBusinessRows(jdbc, sales);

        mockMvc.perform(as(SOURCE_ONLY, get("/api/v1/dashboard/overview")))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(TENANT_READER, put("/api/v1/dashboard/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new DashboardPreferenceUpdateRequest()
                                .setTimeRange("CURRENT_MONTH")
                                .setDataScope("DEPARTMENT")))))
                .andExpect(status().isOk());

        DashboardOverviewResponse narrowed = overview(TENANT_READER);
        assertEquals(AS_OF_DATE, narrowed.getAsOfDate());
        assertEquals("CURRENT_MONTH", narrowed.getTimeRange());
        assertEquals(LocalDate.of(2026, 7, 1), narrowed.getFrom());
        assertEquals(LocalDate.of(2026, 7, 31), narrowed.getTo());
        assertEquals("DEPARTMENT", narrowed.getPreferredDataScope());
        assertEquals("DAY", narrowed.getGranularity());
        assertEquals(List.of("WON_OPPORTUNITY", "ACTIVE_CONTRACT", "ACTIVE_RECEIVABLE"),
                narrowed.getCards().stream().map(
                        DashboardOverviewResponse.CardResponse::getMetric).toList());
        for (DashboardOverviewResponse.CardResponse card : narrowed.getCards()) {
            assertEquals("AVAILABLE", card.getStatus());
            assertEquals(2L, card.getCount());
            assertEquals(new BigDecimal("30.30"), card.getAmount());
            assertEquals(31, card.getPoints().size());
            assertEquals(card.getCount().longValue(), card.getPoints().stream()
                    .mapToLong(DashboardOverviewResponse.PointResponse::getCount).sum());
        }
        assertEquals(List.of("NEW_CUSTOMER", "NEW_CONTACT", "NEW_OPPORTUNITY", "NEW_CONTRACT",
                        "CONTRACT_AMOUNT", "OPPORTUNITY_AMOUNT", "RECEIVABLE_AMOUNT", "NEW_LEAD",
                        "NEW_FOLLOW_UP"),
                narrowed.getSalesBrief().stream().map(
                        DashboardOverviewResponse.SalesBriefResponse::getMetric).toList());
        assertEquals(List.of("AVAILABLE", "UNAVAILABLE", "AVAILABLE", "AVAILABLE",
                        "AVAILABLE", "AVAILABLE", "AVAILABLE", "UNAVAILABLE", "UNAVAILABLE"),
                narrowed.getSalesBrief().stream().map(
                        DashboardOverviewResponse.SalesBriefResponse::getStatus).toList());
        assertEquals(List.of("NEW_CUSTOMER", "CONVERTED_CUSTOMER", "CUSTOMER_RELEASED_TO_POOL",
                        "CUSTOMER_CLAIMED_FROM_POOL", "NEW_OPPORTUNITY", "WON_OPPORTUNITY",
                        "LOST_OPPORTUNITY", "OPPORTUNITY_AMOUNT", "SIGNED_CONTRACT",
                        "EXPIRING_CONTRACT", "EXPIRED_CONTRACT", "CONTRACT_AMOUNT",
                        "FOLLOWED_CUSTOMER", "NEW_UNFOLLOWED_CUSTOMER", "RECEIVABLE_AMOUNT",
                        "EXPECTED_RECEIVABLE"),
                narrowed.getDataSummary().stream().map(
                        DashboardOverviewResponse.DataSummaryResponse::getMetric).toList());
        assertEquals(List.of("AVAILABLE", "AVAILABLE", "AVAILABLE", "AVAILABLE",
                        "AVAILABLE", "AVAILABLE", "AVAILABLE", "AVAILABLE", "AVAILABLE",
                        "AVAILABLE", "AVAILABLE", "AVAILABLE", "AVAILABLE", "AVAILABLE",
                        "AVAILABLE", "AVAILABLE"), narrowed.getDataSummary().stream().map(
                        DashboardOverviewResponse.DataSummaryResponse::getStatus).toList());
        DashboardOverviewResponse.DataSummaryResponse lostOpportunity = narrowed.getDataSummary().stream()
                .filter(summary -> "LOST_OPPORTUNITY".equals(summary.getMetric())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("1"), lostOpportunity.getValue());
        DashboardOverviewResponse.DataSummaryResponse convertedCustomer = narrowed.getDataSummary()
                .stream().filter(summary -> "CONVERTED_CUSTOMER".equals(summary.getMetric()))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("1"), convertedCustomer.getValue());
        DashboardOverviewResponse.DataSummaryResponse expectedReceivable = narrowed.getDataSummary().stream()
                .filter(summary -> "EXPECTED_RECEIVABLE".equals(summary.getMetric())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("30.30"), expectedReceivable.getValue());
        DashboardOverviewResponse.DataSummaryResponse expiringContract = narrowed.getDataSummary().stream()
                .filter(summary -> "EXPIRING_CONTRACT".equals(summary.getMetric())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("1"), expiringContract.getValue());
        DashboardOverviewResponse.DataSummaryResponse expiredContract = narrowed.getDataSummary().stream()
                .filter(summary -> "EXPIRED_CONTRACT".equals(summary.getMetric())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("1"), expiredContract.getValue());
        DashboardOverviewResponse.DataSummaryResponse followedCustomer = narrowed.getDataSummary().stream()
                .filter(summary -> "FOLLOWED_CUSTOMER".equals(summary.getMetric())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("1"), followedCustomer.getValue());
        DashboardOverviewResponse.DataSummaryResponse newUnfollowedCustomer = narrowed.getDataSummary()
                .stream().filter(summary -> "NEW_UNFOLLOWED_CUSTOMER".equals(summary.getMetric()))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("2"), newUnfollowedCustomer.getValue());
        DashboardOverviewResponse.DataSummaryResponse releasedToPool = narrowed.getDataSummary()
                .stream().filter(summary -> "CUSTOMER_RELEASED_TO_POOL".equals(summary.getMetric()))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("2"), releasedToPool.getValue());
        DashboardOverviewResponse.DataSummaryResponse claimedFromPool = narrowed.getDataSummary()
                .stream().filter(summary -> "CUSTOMER_CLAIMED_FROM_POOL".equals(summary.getMetric()))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("2"), claimedFromPool.getValue());
        assertEquals("AVAILABLE", narrowed.getSalesFunnel().getStatus());
        assertEquals("direct", narrowed.getSalesFunnel().getPipelines().getFirst().getCode());
        DashboardOverviewResponse.SalesFunnelStageResponse funnelStage = narrowed.getSalesFunnel()
                .getPipelines().getFirst().getStages().getFirst();
        assertEquals("qualification", funnelStage.getCode());
        assertEquals(2L, funnelStage.getCount());
        assertEquals(new BigDecimal("30.30"), funnelStage.getAmount());
        assertEquals("AVAILABLE", narrowed.getCustomerContactReminder().getStatus());
        assertEquals(List.of("OVER_7_DAYS", "OVER_15_DAYS", "OVER_30_DAYS",
                        "OVER_3_MONTHS", "OVER_6_MONTHS", "OVERDUE"),
                narrowed.getCustomerContactReminder().getBuckets().stream()
                        .map(DashboardOverviewResponse.CustomerContactReminderBucketResponse::getBucket)
                        .toList());
        assertEquals(List.of(2L, 2L, 2L, 1L, 1L, 1L),
                narrowed.getCustomerContactReminder().getBuckets().stream()
                        .map(DashboardOverviewResponse.CustomerContactReminderBucketResponse::getCount)
                        .toList());
        assertEquals(List.of("WON_OPPORTUNITY", "ACTIVE_CONTRACT", "ACTIVE_RECEIVABLE"),
                narrowed.getRankings().stream().map(
                        DashboardOverviewResponse.RankingResponse::getMetric).toList());
        for (DashboardOverviewResponse.RankingResponse ranking : narrowed.getRankings()) {
            assertEquals("AVAILABLE", ranking.getStatus());
            assertEquals(2, ranking.getItems().size());
            assertEquals(1, ranking.getItems().getFirst().getRank());
            assertEquals(2, ranking.getItems().getLast().getRank());
        }

        hideField(jdbc, tenantRole, "CONTRACT", "total_amount");
        DashboardOverviewResponse fieldRestricted = overview(TENANT_READER);
        assertEquals("AVAILABLE", fieldRestricted.getCards().get(0).getStatus());
        DashboardOverviewResponse.CardResponse hiddenContract =
                fieldRestricted.getCards().get(1);
        assertEquals("UNAVAILABLE", hiddenContract.getStatus());
        assertNull(hiddenContract.getCount());
        assertNull(hiddenContract.getAmount());
        assertTrue(hiddenContract.getPoints().isEmpty());
        assertEquals("AVAILABLE", fieldRestricted.getCards().get(2).getStatus());
        DashboardOverviewResponse.RankingResponse hiddenContractRanking =
                fieldRestricted.getRankings().get(1);
        assertEquals("UNAVAILABLE", hiddenContractRanking.getStatus());
        assertTrue(hiddenContractRanking.getItems().isEmpty());
        assertEquals("AVAILABLE", fieldRestricted.getSalesBrief().get(3).getStatus());
        assertEquals("UNAVAILABLE", fieldRestricted.getSalesBrief().get(4).getStatus());
        assertEquals("AVAILABLE", fieldRestricted.getSalesFunnel().getStatus());
        DashboardOverviewResponse.DataSummaryResponse stillVisibleExpiring = fieldRestricted
                .getDataSummary().stream().filter(summary -> "EXPIRING_CONTRACT".equals(
                        summary.getMetric())).findFirst().orElseThrow();
        assertEquals("AVAILABLE", stillVisibleExpiring.getStatus());
        assertEquals(new BigDecimal("1"), stillVisibleExpiring.getValue());
        hideField(jdbc, tenantRole, "CUSTOMER", "deal_status");
        DashboardOverviewResponse dealStatusFieldRestricted = overview(TENANT_READER);
        DashboardOverviewResponse.DataSummaryResponse hiddenConvertedCustomer =
                dealStatusFieldRestricted.getDataSummary().stream()
                        .filter(summary -> "CONVERTED_CUSTOMER".equals(summary.getMetric()))
                        .findFirst().orElseThrow();
        assertEquals("UNAVAILABLE", hiddenConvertedCustomer.getStatus());
        assertNull(hiddenConvertedCustomer.getValue());
        hideField(jdbc, tenantRole, "CONTRACT", "end_date");
        DashboardOverviewResponse endDateFieldRestricted = overview(TENANT_READER);
        for (String metric : List.of("EXPIRING_CONTRACT", "EXPIRED_CONTRACT")) {
            DashboardOverviewResponse.DataSummaryResponse hiddenExpiration = endDateFieldRestricted
                    .getDataSummary().stream().filter(summary -> metric.equals(summary.getMetric()))
                    .findFirst().orElseThrow();
            assertEquals("UNAVAILABLE", hiddenExpiration.getStatus());
            assertNull(hiddenExpiration.getValue());
        }
        hideField(jdbc, tenantRole, "OPPORTUNITY", "outcome_time");
        DashboardOverviewResponse outcomeFieldRestricted = overview(TENANT_READER);
        DashboardOverviewResponse.DataSummaryResponse hiddenLostOpportunity = outcomeFieldRestricted
                .getDataSummary().stream()
                .filter(summary -> "LOST_OPPORTUNITY".equals(summary.getMetric()))
                .findFirst().orElseThrow();
        assertEquals("UNAVAILABLE", hiddenLostOpportunity.getStatus());
        assertNull(hiddenLostOpportunity.getValue());
        hideField(jdbc, tenantRole, "OPPORTUNITY", "estimated_amount");
        DashboardOverviewResponse funnelFieldRestricted = overview(TENANT_READER);
        assertEquals("UNAVAILABLE", funnelFieldRestricted.getSalesFunnel().getStatus());
        hideField(jdbc, tenantRole, "RECEIVABLE_PLAN", "planned_amount");
        DashboardOverviewResponse planFieldRestricted = overview(TENANT_READER);
        DashboardOverviewResponse.DataSummaryResponse hiddenExpectedReceivable = planFieldRestricted
                .getDataSummary().stream()
                .filter(summary -> "EXPECTED_RECEIVABLE".equals(summary.getMetric()))
                .findFirst().orElseThrow();
        assertEquals("UNAVAILABLE", hiddenExpectedReceivable.getStatus());
        assertNull(hiddenExpectedReceivable.getValue());
        hideField(jdbc, tenantRole, "CUSTOMER", "created_time");
        DashboardOverviewResponse customerFieldRestricted = overview(TENANT_READER);
        for (String metric : List.of("FOLLOWED_CUSTOMER", "NEW_UNFOLLOWED_CUSTOMER")) {
            DashboardOverviewResponse.DataSummaryResponse hiddenFollowUp = customerFieldRestricted
                    .getDataSummary().stream().filter(summary -> metric.equals(summary.getMetric()))
                    .findFirst().orElseThrow();
            assertEquals("UNAVAILABLE", hiddenFollowUp.getStatus());
            assertNull(hiddenFollowUp.getValue());
        }

        long customerId = jdbc.queryForObject(
                "SELECT id FROM crm_customer WHERE tenant_id = ? AND name = 'B18b customer'",
                Long.class, TENANT_ID);
        jdbc.update("INSERT INTO crm_activity "
                        + "(tenant_id, subject_type, subject_id, kind, creator_actor_id, content, "
                        + "occurred_time, status, created_time, updated_time) "
                        + "VALUES (?, 'CUSTOMER', ?, 'FOLLOW_UP', ?, 'Dashboard follow-up', ?, "
                        + "'ACTIVE', ?, ?)",
                TENANT_ID, customerId, TENANT_READER, AS_OF_DATE.atTime(9, 0),
                AS_OF_DATE.atTime(9, 0), AS_OF_DATE.atTime(9, 0));
        long preferenceVersion = jdbc.queryForObject(
                "SELECT version FROM dashboard_preference WHERE tenant_id = ? AND actor_id = ?",
                Long.class, TENANT_ID, TENANT_READER);
        mockMvc.perform(as(TENANT_READER, put("/api/v1/dashboard/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new DashboardPreferenceUpdateRequest()
                                .setTimeRange("CURRENT_MONTH").setDataScope("SELF")
                                .setVersion(preferenceVersion)))))
                .andExpect(status().isOk());
        DashboardOverviewResponse self = overview(TENANT_READER);
        DashboardOverviewResponse.SalesBriefResponse followUp = self.getSalesBrief().getLast();
        assertEquals("AVAILABLE", followUp.getStatus());
        assertEquals(new BigDecimal("1"), followUp.getValue());
        assertEquals(new BigDecimal("1"), followUp.getPreviousValue());
        assertEquals(new BigDecimal("0.00"), followUp.getComparisonRate());

        long before = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dashboard_preference WHERE tenant_id = ? "
                        + "AND actor_id = ?", Long.class, TENANT_ID, OPPORTUNITY_ONLY);
        DashboardOverviewResponse defaults = overview(OPPORTUNITY_ONLY);
        long after = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dashboard_preference WHERE tenant_id = ? "
                        + "AND actor_id = ?", Long.class, TENANT_ID, OPPORTUNITY_ONLY);
        assertEquals(0L, before);
        assertEquals(0L, after);
        assertEquals("CURRENT_MONTH", defaults.getTimeRange());
        assertEquals("SELF", defaults.getPreferredDataScope());
        assertEquals("AVAILABLE", defaults.getCards().get(0).getStatus());
        assertEquals(1L, defaults.getCards().get(0).getCount());
        assertEquals(new BigDecimal("5.05"), defaults.getCards().get(0).getAmount());
        assertEquals("AVAILABLE", defaults.getRankings().get(0).getStatus());
        assertEquals(1, defaults.getRankings().get(0).getItems().size());
        for (int index : List.of(1, 2)) {
            DashboardOverviewResponse.CardResponse unavailable = defaults.getCards().get(index);
            assertEquals("UNAVAILABLE", unavailable.getStatus());
            assertNull(unavailable.getCount());
            assertNull(unavailable.getAmount());
            assertTrue(unavailable.getPoints().isEmpty());
            DashboardOverviewResponse.RankingResponse unavailableRanking = defaults.getRankings().get(index);
            assertEquals("UNAVAILABLE", unavailableRanking.getStatus());
            assertTrue(unavailableRanking.getItems().isEmpty());
        }
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
                                .setDisplayName("B18b owner")))
                        .header("X-Dev-User-Id", OWNER_ID)
                        .header("X-Dev-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk());
    }

    private long department(JdbcTemplate jdbc, long parentId, String name) {
        jdbc.update("INSERT INTO organization_department "
                        + "(tenant_id, parent_department_id, name, status) "
                        + "VALUES (?, ?, ?, 'ACTIVE')", TENANT_ID, parentId, name);
        return jdbc.queryForObject("SELECT id FROM organization_department "
                        + "WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, name);
    }

    private void member(
            JdbcTemplate jdbc, long actorId, long departmentId, String displayName) {
        jdbc.update("INSERT INTO organization_member_profile "
                        + "(actor_id, display_name) VALUES (?, ?)", actorId, displayName);
        jdbc.update("INSERT INTO organization_membership "
                        + "(tenant_id, actor_id, department_id, status) "
                        + "VALUES (?, ?, ?, 'ACTIVE')",
                TENANT_ID, actorId, departmentId);
    }

    private long role(
            JdbcTemplate jdbc, long actorId, String scope,
            boolean reports, List<String> objectTypes) {
        String name = "B18b " + actorId;
        jdbc.update("INSERT INTO authorization_role "
                        + "(tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, name);
        long roleId = jdbc.queryForObject("SELECT id FROM authorization_role "
                        + "WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, name);
        if (reports) {
            grant(jdbc, roleId, "reports:read", null);
        }
        if (objectTypes.contains("CUSTOMER")) {
            grant(jdbc, roleId, "customers:read", scope);
            allFields(jdbc, roleId, "CUSTOMER");
        }
        if (objectTypes.contains("OPPORTUNITY")) {
            grant(jdbc, roleId, "opportunities:read", scope);
            allFields(jdbc, roleId, "OPPORTUNITY");
        }
        if (objectTypes.contains("CONTRACT")) {
            grant(jdbc, roleId, "contracts:read", scope);
            allFields(jdbc, roleId, "CONTRACT");
        }
        if (objectTypes.contains("RECEIVABLE")) {
            grant(jdbc, roleId, "receivables:read", scope);
            allFields(jdbc, roleId, "RECEIVABLE");
        }
        if (objectTypes.contains("RECEIVABLE_PLAN")) {
            grant(jdbc, roleId, "receivable-plans:read", scope);
            allFields(jdbc, roleId, "RECEIVABLE_PLAN");
        }
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT_ID, actorId, roleId);
        return roleId;
    }

    private void grant(
            JdbcTemplate jdbc, long roleId, String authority, String dataScope) {
        jdbc.update("INSERT INTO authorization_role_grant "
                        + "(tenant_id, role_id, authority_code, data_scope) "
                        + "VALUES (?, ?, ?, ?)",
                TENANT_ID, roleId, authority, dataScope);
    }

    private void allFields(JdbcTemplate jdbc, long roleId, String objectType) {
        jdbc.update("INSERT INTO role_field_policy "
                        + "(tenant_id, role_id, object_type) VALUES (?, ?, ?)",
                TENANT_ID, roleId, objectType);
        jdbc.update("INSERT INTO role_field_access "
                        + "(tenant_id, role_id, object_type, field_id, "
                        + "access_level, mask_type) "
                        + "SELECT ?, ?, ?, id, 'READ', 'NONE' "
                        + "FROM custom_field_definition WHERE tenant_id = ? "
                        + "AND object_type = ? AND is_builtin = 1",
                TENANT_ID, roleId, objectType, TENANT_ID, objectType);
    }

    private void seedBusinessRows(JdbcTemplate jdbc, long salesDepartmentId) {
        jdbc.update("INSERT INTO crm_customer "
                        + "(tenant_id, owner_actor_id, name, status, deal_status, created_time, updated_time) "
                        + "VALUES (?, ?, 'B18b customer', 'ACTIVE', 'OPEN', ?, ?)",
                TENANT_ID, TENANT_READER, AS_OF_DATE.minusDays(31).atTime(9, 0),
                AS_OF_DATE.minusDays(31).atTime(9, 0));
        long customerId = jdbc.queryForObject(
                "SELECT id FROM crm_customer WHERE tenant_id = ? AND name = 'B18b customer'",
                Long.class, TENANT_ID);
        long poolId = customerPool(jdbc, salesDepartmentId);
        customerPoolRelease(jdbc, poolId, customerId, TENANT_READER);
        customerPoolRelease(jdbc, poolId, customerId, PEER_OWNER);
        customerPoolRelease(jdbc, poolId, customerId, WEST_OWNER);
        customerPoolClaim(jdbc, poolId, customerId, TENANT_READER, false);
        customerPoolClaim(jdbc, poolId, customerId, PEER_OWNER, false);
        customerPoolClaim(jdbc, poolId, customerId, WEST_OWNER, false);
        customerPoolClaim(jdbc, poolId, customerId, WEST_OWNER, true);
        jdbc.update("INSERT INTO crm_customer "
                        + "(tenant_id, owner_actor_id, name, status, deal_status, created_time, updated_time) "
                        + "VALUES (?, ?, 'B18b followed customer', 'ACTIVE', 'OPEN', ?, ?)",
                TENANT_ID, TENANT_READER, AS_OF_DATE.minusDays(1).atTime(9, 0),
                AS_OF_DATE.minusDays(1).atTime(9, 0));
        long followedCustomerId = jdbc.queryForObject(
                "SELECT id FROM crm_customer WHERE tenant_id = ? AND name = 'B18b followed customer'",
                Long.class, TENANT_ID);
        jdbc.update("INSERT INTO crm_customer "
                        + "(tenant_id, owner_actor_id, name, status, deal_status, created_time, updated_time) "
                        + "VALUES (?, ?, 'B18b new unfollowed customer', 'ACTIVE', 'OPEN', ?, ?)",
                TENANT_ID, TENANT_READER, AS_OF_DATE.atTime(9, 0), AS_OF_DATE.atTime(9, 0));
        jdbc.update("INSERT INTO crm_customer "
                        + "(tenant_id, owner_actor_id, name, status, deal_status, deal_time, created_time, updated_time) "
                        + "VALUES (?, ?, 'B18b converted customer', 'ACTIVE', 'WON', ?, ?, ?)",
                TENANT_ID, TENANT_READER, AS_OF_DATE.atTime(11, 0),
                AS_OF_DATE.minusDays(2).atTime(9, 0), AS_OF_DATE.atTime(11, 0));
        jdbc.update("INSERT INTO crm_customer "
                        + "(tenant_id, owner_actor_id, name, status, deal_status, created_time, updated_time) "
                        + "VALUES (?, ?, 'B18b aged customer', 'ACTIVE', 'OPEN', ?, ?)",
                TENANT_ID, TENANT_READER, AS_OF_DATE.minusMonths(7).atTime(9, 0),
                AS_OF_DATE.minusMonths(7).atTime(9, 0));
        jdbc.update("INSERT INTO crm_activity "
                        + "(tenant_id, subject_type, subject_id, kind, creator_actor_id, content, "
                        + "occurred_time, next_contact_time, status, created_time, updated_time) "
                        + "VALUES (?, 'CUSTOMER', ?, 'FOLLOW_UP', ?, 'Reminder follow-up', ?, ?, "
                        + "'ACTIVE', ?, ?)",
                TENANT_ID, customerId, TENANT_READER, AS_OF_DATE.minusDays(31).atTime(9, 0),
                AS_OF_DATE.minusDays(1).atTime(9, 0), AS_OF_DATE.minusDays(31).atTime(9, 0),
                AS_OF_DATE.minusDays(31).atTime(9, 0));
        jdbc.update("INSERT INTO crm_activity "
                        + "(tenant_id, subject_type, subject_id, kind, creator_actor_id, content, "
                        + "occurred_time, status, created_time, updated_time) "
                        + "VALUES (?, 'CUSTOMER', ?, 'FOLLOW_UP', ?, 'Current dashboard follow-up', ?, "
                        + "'ACTIVE', ?, ?)",
                TENANT_ID, followedCustomerId, PEER_OWNER, AS_OF_DATE.atTime(10, 0),
                AS_OF_DATE.atTime(10, 0), AS_OF_DATE.atTime(10, 0));
        Map<Long, String> amounts = Map.of(
                TENANT_READER, "10.10",
                PEER_OWNER, "20.20",
                WEST_OWNER, "40.40");
        for (Map.Entry<Long, String> entry : amounts.entrySet()) {
            opportunity(jdbc, entry.getKey(), customerId,
                    "owner-" + entry.getKey(), entry.getValue());
            long contractId = contract(jdbc, entry.getKey(), customerId,
                    "owner-" + entry.getKey(), entry.getValue(), contractEndDate(entry.getKey()));
            receivable(jdbc, entry.getKey(), customerId, contractId,
                    "owner-" + entry.getKey(), entry.getValue());
            receivablePlan(jdbc, entry.getKey(), customerId, contractId,
                    "owner-" + entry.getKey(), entry.getValue());
        }
        opportunity(jdbc, OPPORTUNITY_ONLY, customerId,
                "opportunity-only", "5.05");
        opportunity(jdbc, TENANT_READER, customerId,
                "lost-result", "7.07", "LOST", AS_OF_DATE.minusMonths(1));
    }

    private void opportunity(
            JdbcTemplate jdbc, long ownerId, long customerId,
            String key, String amount) {
        opportunity(jdbc, ownerId, customerId, key, amount, "WON", AS_OF_DATE);
    }

    private long customerPool(JdbcTemplate jdbc, long eligibleDepartmentId) {
        jdbc.update("INSERT INTO crm_customer_pool "
                        + "(tenant_id, name, status, daily_claim_limit, "
                        + "previous_owner_cooldown_days, automatic_release_enabled, version) "
                        + "VALUES (?, 'B18b Sales pool', 'ACTIVE', 0, 0, 0, 0)",
                TENANT_ID);
        long poolId = jdbc.queryForObject("SELECT id FROM crm_customer_pool "
                        + "WHERE tenant_id = ? AND name = 'B18b Sales pool'",
                Long.class, TENANT_ID);
        jdbc.update("INSERT INTO crm_customer_pool_eligible_department "
                        + "(tenant_id, pool_id, department_id) VALUES (?, ?, ?)",
                TENANT_ID, poolId, eligibleDepartmentId);
        return poolId;
    }

    private void customerPoolRelease(
            JdbcTemplate jdbc, long poolId, long customerId, long previousOwnerId) {
        jdbc.update("INSERT INTO crm_customer_pool_release_history "
                        + "(tenant_id, pool_id, customer_id, previous_owner_actor_id, released_time) "
                        + "VALUES (?, ?, ?, ?, ?)",
                TENANT_ID, poolId, customerId, previousOwnerId, AS_OF_DATE.atTime(9, 0));
    }

    private void customerPoolClaim(
            JdbcTemplate jdbc, long poolId, long customerId, long actorId, boolean assigned) {
        jdbc.update("INSERT INTO crm_customer_pool_claim_history "
                        + "(tenant_id, pool_id, customer_id, actor_id, is_assigned, claimed_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                TENANT_ID, poolId, customerId, actorId, assigned, AS_OF_DATE.atTime(10, 0));
    }

    private void opportunity(
            JdbcTemplate jdbc, long ownerId, long customerId,
            String key, String amount, String outcome, LocalDate createdDate) {
        OpportunityPipelineFixture.provisionActivePair(jdbc, TENANT_ID, "direct", "qualification");
        jdbc.update("INSERT INTO crm_opportunity "
                        + "(tenant_id, owner_actor_id, customer_id, name, pipeline_code, "
                        + "stage_code, estimated_amount, outcome, outcome_time, status, "
                        + "created_time, updated_time) "
                        + "VALUES (?, ?, ?, ?, 'direct', 'qualification', ?, ?, ?, 'ACTIVE', ?, ?)",
                TENANT_ID, ownerId, customerId, "B18b-" + key,
                new BigDecimal(amount), outcome, AS_OF_DATE.atTime(12, 0),
                createdDate.atTime(12, 0), createdDate.atTime(12, 0));
    }

    private long contract(
            JdbcTemplate jdbc, long ownerId, long customerId,
            String key, String amount, LocalDate endDate) {
        String number = "B18B-" + key;
        jdbc.update("INSERT INTO crm_contract "
                        + "(tenant_id, owner_actor_id, customer_id, name, contract_number, "
                        + "contract_number_key, order_date, subtotal, discount_rate, "
                        + "total_amount, end_date, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0.0000, ?, ?, 'ACTIVE')",
                TENANT_ID, ownerId, customerId, "B18b-" + key, number,
                number.toLowerCase(), AS_OF_DATE, new BigDecimal(amount),
                new BigDecimal(amount), endDate);
        return jdbc.queryForObject("SELECT id FROM crm_contract "
                        + "WHERE tenant_id = ? AND contract_number = ?",
                Long.class, TENANT_ID, number);
    }

    private LocalDate contractEndDate(long ownerId) {
        if (ownerId == TENANT_READER) {
            return AS_OF_DATE;
        }
        if (ownerId == PEER_OWNER) {
            return AS_OF_DATE.minusDays(1);
        }
        return AS_OF_DATE.plusDays(1);
    }

    private void receivable(
            JdbcTemplate jdbc, long ownerId, long customerId,
            long contractId, String key, String amount) {
        jdbc.update("INSERT INTO crm_receivable "
                        + "(tenant_id, owner_actor_id, customer_id, contract_id, "
                        + "receipt_number, received_date, payment_method, amount, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'BANK', ?, 'ACTIVE')",
                TENANT_ID, ownerId, customerId, contractId, "B18BR-" + key,
                AS_OF_DATE, new BigDecimal(amount));
    }

    private void receivablePlan(
            JdbcTemplate jdbc, long ownerId, long customerId,
            long contractId, String key, String amount) {
        jdbc.update("INSERT INTO crm_receivable_plan "
                        + "(tenant_id, owner_actor_id, customer_id, contract_id, installment, "
                        + "due_date, planned_amount, payment_method, status) "
                        + "VALUES (?, ?, ?, ?, 1, ?, ?, 'BANK', 'ACTIVE')",
                TENANT_ID, ownerId, customerId, contractId, AS_OF_DATE, new BigDecimal(amount));
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

    private MockHttpServletRequestBuilder as(
            long actorId, MockHttpServletRequestBuilder request) {
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

    /** Keep this early-sorted integration class isolated from the shared MySQL suite. */
    private void clearOwnTenant() {
        executeSql("DELETE FROM dashboard_performance_target WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_activity_relation WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_activity WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_customer_pool_release_history WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_customer_pool_claim_history WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_customer_pool_membership WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_receivable WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_receivable_plan WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_contract WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_opportunity WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_opportunity_pipeline_stage WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_opportunity_pipeline WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_opportunity_pipeline_catalog_guard WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_customer WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM dashboard_preference WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM role_field_access WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM role_field_policy WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM authorization_role_assignment WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM authorization_role_grant WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM authorization_role WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM custom_field_definition WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM customization_object_guard WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_activity_option WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_product_catalog_guard WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_customer_pool_automation_cursor WHERE tenant_id = "
                + TENANT_ID);
        executeSql("DELETE FROM crm_customer_pool_administrator WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_customer_pool_eligible_member WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_customer_pool_eligible_department WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_customer_pool_rule WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_customer_pool WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM crm_contact_pool_automation_cursor WHERE tenant_id = "
                + TENANT_ID);
        executeSql("DELETE FROM organization_membership WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM organization_department WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM organization_tenant WHERE tenant_id = " + TENANT_ID);
        executeSql("DELETE FROM organization_member_profile WHERE actor_id BETWEEN "
                + OWNER_ID + " AND " + SOURCE_ONLY);
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
