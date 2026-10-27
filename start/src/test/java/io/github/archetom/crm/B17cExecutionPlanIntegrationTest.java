package io.github.archetom.crm;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real-MySQL index and narrow-range execution-plan regression checks for B17c. */
@RequiresDatabase
class B17cExecutionPlanIntegrationTest extends BaseIntegrationTest {

    private static final long TENANT = 1_717_950L;
    private static final long ACTOR = 1_729_050L;
    private static final long CUSTOMER = 1_739_050L;
    private static final int ROOT_ROWS = 20_000;

    @Test
    void narrowBuiltInAndCustomTimeRangesUseDateLeadingIndexes() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        seed(jdbc);
        assertCustomReportIndexes(jdbc);

        LocalDateTime fromTime = LocalDate.of(2025, 1, 1).atStartOfDay();
        LocalDateTime toTime = LocalDate.of(2025, 1, 2).atStartOfDay();
        assertRangePlan(jdbc, """
                EXPLAIN SELECT DATE(report_root.outcome_time), COUNT(*),
                               COALESCE(SUM(report_root.estimated_amount), 0)
                FROM crm_opportunity report_root
                WHERE report_root.tenant_id = ?
                  AND report_root.status = 'ACTIVE'
                  AND report_root.outcome_time >= ?
                  AND report_root.outcome_time < ?
                GROUP BY DATE(report_root.outcome_time)
                LIMIT 501
                """, "report_root", "idx_crm_opportunity_report_outcome_time",
                TENANT, fromTime, toTime);

        assertRangePlan(jdbc, """
                EXPLAIN SELECT report_root.order_date, COUNT(*),
                               COALESCE(SUM(report_root.total_amount), 0)
                FROM crm_contract report_root
                WHERE report_root.tenant_id = ?
                  AND report_root.status IN ('ACTIVE', 'VOIDED')
                  AND report_root.order_date >= ?
                  AND report_root.order_date < ?
                GROUP BY report_root.order_date
                LIMIT 501
                """, "report_root", "idx_crm_contract_report_date",
                TENANT, fromTime.toLocalDate(), toTime.toLocalDate());

        assertRangePlan(jdbc, """
                EXPLAIN SELECT report_root.received_date, COUNT(*),
                               COALESCE(SUM(report_root.amount), 0)
                FROM crm_receivable report_root
                WHERE report_root.tenant_id = ?
                  AND report_root.status = 'ACTIVE'
                  AND report_root.received_date >= ?
                  AND report_root.received_date < ?
                GROUP BY report_root.received_date
                LIMIT 501
                """, "report_root", "idx_crm_receivable_report_date",
                TENANT, fromTime.toLocalDate(), toTime.toLocalDate());

        Long customTimeField = jdbc.queryForObject("SELECT id FROM custom_field_definition "
                        + "WHERE tenant_id = ? AND object_type = 'OPPORTUNITY' "
                        + "AND field_code = 'cf_plan_time'",
                Long.class, TENANT);
        assertRangePlan(jdbc, """
                EXPLAIN SELECT DATE(report_value_0.scalar_value), COUNT(*)
                FROM crm_opportunity report_root
                LEFT JOIN crm_opportunity_field_value report_value_0
                  ON report_value_0.tenant_id = report_root.tenant_id
                 AND report_value_0.opportunity_id = report_root.id
                 AND report_value_0.field_id = ?
                WHERE report_root.tenant_id = ?
                  AND report_root.status = 'ACTIVE'
                  AND report_value_0.scalar_value >= ?
                  AND report_value_0.scalar_value < ?
                GROUP BY DATE(report_value_0.scalar_value)
                LIMIT 501
                """, "report_value_0", "idx_crm_opportunity_field_value_search",
                customTimeField, TENANT, "2025-01-01T00:00", "2025-01-02T00:00");
    }

    @Override
    protected void clearTestData() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM crm_opportunity_field_value WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM crm_receivable WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM crm_contract WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM crm_opportunity WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM crm_customer WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM custom_field_definition WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM organization_membership WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM organization_tenant WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM organization_member_profile WHERE actor_id = ?", ACTOR);
    }

    private void seed(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) "
                + "VALUES (?, 'B17c plan actor')", ACTOR);
        jdbc.update("INSERT INTO organization_tenant (tenant_id, initialized_by_actor_id) "
                + "VALUES (?, ?)", TENANT, ACTOR);
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) "
                + "VALUES (?, ?, 'ACTIVE')", TENANT, ACTOR);
        OpportunityPipelineFixture.provisionActivePair(jdbc, TENANT, "direct", "qualification");
        jdbc.update("INSERT INTO crm_customer "
                        + "(id, tenant_id, owner_actor_id, name, status, deal_status) "
                        + "VALUES (?, ?, ?, 'B17c plan customer', 'ACTIVE', 'OPEN')",
                CUSTOMER, TENANT, ACTOR);
        jdbc.update("INSERT INTO custom_field_definition "
                        + "(tenant_id, object_type, field_code, display_name, field_type, "
                        + "is_builtin, is_required, is_unique, is_searchable, status, "
                        + "display_order, options_json) "
                        + "VALUES (?, 'OPPORTUNITY', 'cf_plan_time', 'Plan time', 'DATETIME', "
                        + "0, 0, 0, 1, 'ACTIVE', 900, JSON_ARRAY())",
                TENANT);

        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET SESSION cte_max_recursion_depth = 25000");
                statement.executeUpdate("""
                        INSERT INTO crm_opportunity
                            (tenant_id, owner_actor_id, customer_id, name, pipeline_code,
                             stage_code, estimated_amount, outcome, outcome_time, status)
                        WITH RECURSIVE sequence_number(value) AS (
                            SELECT 1
                            UNION ALL
                            SELECT value + 1 FROM sequence_number WHERE value < 20000
                        )
                        SELECT 1717950, 1729050, 1739050, CONCAT('plan-opportunity-', value),
                               'direct', 'qualification', MOD(value, 10000) / 10,
                               'WON', TIMESTAMPADD(DAY, MOD(value, 730),
                                                   TIMESTAMP('2024-01-01 00:00:00')),
                               IF(MOD(value, 50) = 0, 'DELETED', 'ACTIVE')
                        FROM sequence_number
                        """);
                statement.executeUpdate("""
                        INSERT INTO crm_contract
                            (id, tenant_id, owner_actor_id, customer_id, name, contract_number,
                             contract_number_key, order_date, subtotal, discount_rate,
                             total_amount, status)
                        WITH RECURSIVE sequence_number(value) AS (
                            SELECT 1
                            UNION ALL
                            SELECT value + 1 FROM sequence_number WHERE value < 20000
                        )
                        SELECT 300000 + value, 1717950, 1729050, 1739050,
                               CONCAT('plan-contract-', value), CONCAT('PC-', value),
                               CONCAT('pc-', value),
                               DATE_ADD('2024-01-01', INTERVAL MOD(value, 730) DAY),
                               MOD(value, 10000) + 100000, 0,
                               MOD(value, 10000) + 100000,
                               CASE WHEN MOD(value, 50) = 0 THEN 'DELETED'
                                    WHEN MOD(value, 17) = 0 THEN 'VOIDED'
                                    ELSE 'ACTIVE' END
                        FROM sequence_number
                        """);
                statement.executeUpdate("""
                        INSERT INTO crm_receivable
                            (id, tenant_id, owner_actor_id, customer_id, contract_id,
                             receipt_number, received_date, payment_method, amount, status)
                        WITH RECURSIVE sequence_number(value) AS (
                            SELECT 1
                            UNION ALL
                            SELECT value + 1 FROM sequence_number WHERE value < 20000
                        )
                        SELECT 400000 + value, 1717950, 1729050, 1739050, 300000 + value,
                               CONCAT('PR-', value),
                               DATE_ADD('2024-01-01', INTERVAL MOD(value, 730) DAY),
                               'BANK', MOD(value, 1000) + 1,
                               IF(MOD(value, 50) = 0, 'DELETED', 'ACTIVE')
                        FROM sequence_number
                        """);
            }
            return null;
        });
        Long customTimeField = jdbc.queryForObject("SELECT id FROM custom_field_definition "
                        + "WHERE tenant_id = ? AND object_type = 'OPPORTUNITY' "
                        + "AND field_code = 'cf_plan_time'",
                Long.class, TENANT);
        jdbc.update("""
                INSERT INTO crm_opportunity_field_value
                    (tenant_id, opportunity_id, field_id, values_json, scalar_value)
                SELECT tenant_id, id, ?,
                       JSON_ARRAY(REPLACE(CAST(outcome_time AS CHAR), ' ', 'T')),
                       REPLACE(CAST(outcome_time AS CHAR), ' ', 'T')
                FROM crm_opportunity
                WHERE tenant_id = ?
                """, customTimeField, TENANT);
        jdbc.execute("ANALYZE TABLE crm_opportunity, crm_opportunity_field_value, "
                + "crm_contract, crm_receivable");
        assertEquals(ROOT_ROWS, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_opportunity WHERE tenant_id = ?",
                Integer.class, TENANT));
    }

    private void assertCustomReportIndexes(JdbcTemplate jdbc) {
        assertIndex(jdbc, "crm_opportunity", "idx_crm_opportunity_report_expected_close",
                List.of("tenant_id", "status", "expected_close_date"));
        assertIndex(jdbc, "crm_opportunity", "idx_crm_opportunity_report_next_contact",
                List.of("tenant_id", "status", "next_contact_time"));
        assertIndex(jdbc, "crm_opportunity", "idx_crm_opportunity_report_outcome_time",
                List.of("tenant_id", "status", "outcome_time"));
        assertIndex(jdbc, "crm_opportunity", "idx_crm_opportunity_report_created_time",
                List.of("tenant_id", "status", "created_time"));
        assertIndex(jdbc, "crm_contract", "idx_crm_contract_report_start_date",
                List.of("tenant_id", "status", "start_date"));
        assertIndex(jdbc, "crm_contract", "idx_crm_contract_report_end_date",
                List.of("tenant_id", "status", "end_date"));
        assertIndex(jdbc, "crm_contract", "idx_crm_contract_report_created_time",
                List.of("tenant_id", "status", "created_time"));
        assertIndex(jdbc, "crm_receivable", "idx_crm_receivable_report_created_time",
                List.of("tenant_id", "status", "created_time"));
    }

    private void assertIndex(
            JdbcTemplate jdbc, String table, String index, List<String> expectedColumns) {
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? "
                        + "AND index_name = ? ORDER BY seq_in_index",
                String.class, table, index);
        assertEquals(expectedColumns, columns, () -> "unexpected index: " + index);
    }

    private void assertRangePlan(
            JdbcTemplate jdbc, String sql, String tableAlias, String expectedIndex,
            Object... parameters) {
        String traditionalExplain = sql.replaceFirst(
                "(?i)^\\s*EXPLAIN\\s+", "EXPLAIN FORMAT=TRADITIONAL ");
        List<Map<String, Object>> plan = jdbc.queryForList(traditionalExplain, parameters);
        Map<String, Object> table = plan.stream()
                .filter(row -> tableAlias.equals(String.valueOf(row.get("table"))))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "table is absent from EXPLAIN: " + tableAlias + " plan=" + plan));
        assertEquals("range", String.valueOf(table.get("type")));
        assertEquals(expectedIndex, String.valueOf(table.get("key")));
        Number estimatedRows = (Number) table.get("rows");
        assertNotNull(estimatedRows);
        assertTrue(estimatedRows.longValue() < ROOT_ROWS / 10,
                () -> "narrow range reads too many rows: " + table);
    }
}
