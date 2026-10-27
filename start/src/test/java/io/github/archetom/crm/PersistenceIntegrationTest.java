package io.github.archetom.crm;

import io.github.archetom.crm.domain.entity.DashboardPreference;
import io.github.archetom.crm.domain.entity.User;
import io.github.archetom.crm.domain.entity.NavigationPreference;
import io.github.archetom.crm.domain.entity.RoleAssignment;
import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.UserAlreadyExistsException;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.DashboardTimeRange;
import io.github.archetom.crm.domain.repository.DashboardPreferenceRepository;
import io.github.archetom.crm.domain.repository.NavigationPreferenceRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.RoleAssignmentRepository;
import io.github.archetom.crm.domain.repository.TenantRoleRepository;
import io.github.archetom.crm.domain.model.UserStatus;
import io.github.archetom.crm.domain.repository.UserRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.Email;
import io.github.archetom.crm.domain.valueobject.PasswordHash;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.UserId;
import io.github.archetom.crm.domain.valueobject.Username;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresDatabase
class PersistenceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRoleRepository tenantRoleRepository;

    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    private NavigationPreferenceRepository navigationPreferenceRepository;

    @Autowired
    private DashboardPreferenceRepository dashboardPreferenceRepository;

    @Test
    void flywayCreatesTheSingleAuthoritativeSchema() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer migrations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
        Integer versionColumns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 't_user' AND column_name = 'version'",
                Integer.class);
        Integer deletedColumns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 't_user' AND column_name = 'deleted_time'",
                Integer.class);
        Integer organizationTables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                        + "AND table_name IN ('organization_member_profile', 'organization_department', "
                        + "'organization_membership', 'organization_membership_request', 'organization_tenant')",
                Integer.class);
        Integer pendingRequestGuardColumns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'organization_membership_request' "
                        + "AND column_name = 'pending_applicant_actor_id'",
                Integer.class);
        Integer pendingRequestGuardIndexes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'organization_membership_request' "
                        + "AND index_name = 'uk_organization_membership_request_pending_applicant'",
                Integer.class);
        Integer authorizationTables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                        + "AND table_name IN ('authorization_role', 'authorization_role_grant', "
                        + "'authorization_role_assignment', 'navigation_preference')",
                Integer.class);
        Integer tenantAuthorizationForeignKeys = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = DATABASE() AND constraint_name IN ("
                        + "'fk_authorization_role_grant_role', "
                        + "'fk_authorization_role_assignment_member', "
                        + "'fk_authorization_role_assignment_role', "
                        + "'fk_navigation_preference_member')",
                Integer.class);
        Integer dashboardPreferenceTables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                        + "AND table_name = 'dashboard_preference'",
                Integer.class);
        Integer dashboardPreferenceForeignKeys = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = DATABASE() "
                        + "AND constraint_name = 'fk_dashboard_preference_member'",
                Integer.class);
        Integer customerPoolTables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                        + "AND table_name IN ('crm_customer_pool', 'crm_customer_pool_administrator', "
                        + "'crm_customer_pool_eligible_member', 'crm_customer_pool_eligible_department', "
                        + "'crm_customer_pool_rule', 'crm_customer_pool_membership', "
                        + "'crm_customer_pool_claim_history', "
                        + "'crm_customer_pool_automation_cursor')",
                Integer.class);
        Integer departmentTenantIdIndexes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'organization_department' "
                        + "AND index_name = 'uk_organization_department_tenant_id'",
                Integer.class);
        Integer returnVisitCustomerForeignKeys = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = DATABASE() AND table_name = 'crm_return_visit' "
                        + "AND constraint_name IN ('fk_crm_return_visit_contact_customer', "
                        + "'fk_crm_return_visit_contract_customer')",
                Integer.class);

        assertTrue(migrations != null && migrations >= 1);
        assertEquals(1, versionColumns);
        assertEquals(0, deletedColumns);
        assertEquals(5, organizationTables);
        assertEquals(1, pendingRequestGuardColumns);
        assertEquals(2, pendingRequestGuardIndexes);
        assertEquals(4, authorizationTables);
        assertEquals(4, tenantAuthorizationForeignKeys);
        assertEquals(1, dashboardPreferenceTables);
        assertEquals(1, dashboardPreferenceForeignKeys);
        assertEquals(8, customerPoolTables);
        assertEquals(2, departmentTenantIdIndexes);
        assertEquals(2, returnVisitCustomerForeignKeys);
    }

    @Test
    void opportunityPipelineMigrationRejectsInvalidHistoryBeforeDdlAndRetriesAfterRepair() {
        try (MigrationTestDatabase upgradeDatabase = MigrationTestDatabase.create()) {
            migrationFlyway(upgradeDatabase, "84").migrate();
            JdbcTemplate jdbc = upgradeJdbc(upgradeDatabase);
            seedLegacyOpportunity(jdbc, "bad pipeline code", "qualification");

            Flyway v85 = migrationFlyway(upgradeDatabase, null);
            assertThrows(FlywayException.class, v85::migrate);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() AND table_name IN "
                            + "('crm_opportunity_pipeline_catalog_guard', 'crm_opportunity_pipeline', "
                            + "'crm_opportunity_pipeline_stage')",
                    Integer.class));
            assertEquals("utf8mb4_unicode_ci", jdbc.queryForObject(
                    "SELECT collation_name FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'crm_opportunity' "
                            + "AND column_name = 'pipeline_code'",
                    String.class));

            jdbc.update("UPDATE crm_opportunity SET pipeline_code = 'direct' "
                    + "WHERE tenant_id = 91 AND name = 'legacy invalid pipeline'");
            v85.repair();
            migrationFlyway(upgradeDatabase, null).migrate();

            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_opportunity_pipeline "
                    + "WHERE tenant_id = 91 AND code = 'direct' AND status = 'ACTIVE'", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_opportunity_pipeline_stage "
                            + "WHERE tenant_id = 91 AND pipeline_code = 'direct' "
                            + "AND code = 'qualification' AND status = 'ACTIVE'",
                    Integer.class));
            assertEquals("ascii_bin", jdbc.queryForObject(
                    "SELECT collation_name FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'crm_opportunity' "
                            + "AND column_name = 'pipeline_code'",
                    String.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) "
                            + "FROM information_schema.referential_constraints "
                            + "WHERE constraint_schema = DATABASE() "
                            + "AND constraint_name = 'fk_crm_opportunity_pipeline_stage_pair'",
                    Integer.class));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "INSERT INTO crm_opportunity (tenant_id, owner_actor_id, customer_id, name, "
                            + "pipeline_code, stage_code, outcome, status) "
                            + "VALUES (91, 91, 1, 'invalid pair after V85', 'direct', 'missing', "
                            + "'OPEN', 'ACTIVE')"));
        }
    }

    @Test
    void opportunityPipelineMigrationPreservesCaseVariantsAndExistingSnapshots() {
        try (MigrationTestDatabase upgradeDatabase = MigrationTestDatabase.create()) {
            migrationFlyway(upgradeDatabase, "84").migrate();
            JdbcTemplate jdbc = upgradeJdbc(upgradeDatabase);
            seedV84PipelineUpgradeFixtures(jdbc);
            Map<String, String> snapshots = Map.of(
                    "savedView", jdbc.queryForObject("SELECT CAST(conditions_json AS CHAR) "
                            + "FROM saved_view WHERE id = 101", String.class),
                    "report", jdbc.queryForObject("SELECT CONCAT(CAST(filters_json AS CHAR), '|', "
                            + "CAST(measures_json AS CHAR)) FROM report_definition WHERE id = 201",
                            String.class),
                    "approval", jdbc.queryForObject("SELECT CAST(facts_json AS CHAR) "
                            + "FROM approval_instance WHERE id = 302", String.class),
                    "workflow", jdbc.queryForObject("SELECT CAST(progress_json AS CHAR) "
                            + "FROM stage_workflow_record WHERE id = 401", String.class));

            migrationFlyway(upgradeDatabase, null).migrate();

            assertEquals(List.of("Direct", "direct"), jdbc.queryForList(
                    "SELECT code FROM crm_opportunity_pipeline WHERE tenant_id = 91 ORDER BY code",
                    String.class));
            assertEquals(List.of("Direct/Qualification", "direct/qualification"), jdbc.queryForList(
                    "SELECT CONCAT(pipeline_code, '/', code) FROM crm_opportunity_pipeline_stage "
                            + "WHERE tenant_id = 91 ORDER BY pipeline_code, code",
                    String.class));
            assertEquals(List.of(17L, 18L), jdbc.queryForList(
                    "SELECT version FROM crm_opportunity WHERE tenant_id = 91 ORDER BY id", Long.class));
            assertEquals(snapshots.get("savedView"), jdbc.queryForObject(
                    "SELECT CAST(conditions_json AS CHAR) FROM saved_view WHERE id = 101", String.class));
            assertEquals(snapshots.get("report"), jdbc.queryForObject(
                    "SELECT CONCAT(CAST(filters_json AS CHAR), '|', CAST(measures_json AS CHAR)) "
                            + "FROM report_definition WHERE id = 201", String.class));
            assertEquals(snapshots.get("approval"), jdbc.queryForObject(
                    "SELECT CAST(facts_json AS CHAR) FROM approval_instance WHERE id = 302", String.class));
            assertEquals(snapshots.get("workflow"), jdbc.queryForObject(
                    "SELECT CAST(progress_json AS CHAR) FROM stage_workflow_record WHERE id = 401",
                    String.class));

            jdbc.update("INSERT INTO crm_opportunity_pipeline "
                            + "(tenant_id, code, name, display_order, status) "
                            + "VALUES (91, 'other', 'Other', 10, 'ACTIVE')");
            jdbc.update("INSERT INTO crm_opportunity_pipeline_stage "
                            + "(tenant_id, pipeline_code, code, name, display_order, probability, status) "
                            + "VALUES (91, 'other', 'other-stage', 'Other stage', 10, 0, 'ACTIVE')");
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "INSERT INTO crm_opportunity (tenant_id, owner_actor_id, customer_id, name, "
                            + "pipeline_code, stage_code, outcome, status) "
                            + "VALUES (91, 91, 1, 'cross pipeline pair', 'direct', 'other-stage', "
                            + "'OPEN', 'ACTIVE')"));

            seedLegacyTenant(jdbc, 92, 92, 2, "Second tenant owner", "Second tenant customer");
            jdbc.update("INSERT INTO crm_opportunity_pipeline "
                            + "(tenant_id, code, name, display_order, status) "
                            + "VALUES (92, 'direct', 'Direct', 10, 'ACTIVE')");
            jdbc.update("INSERT INTO crm_opportunity_pipeline_stage "
                            + "(tenant_id, pipeline_code, code, name, display_order, probability, status) "
                            + "VALUES (92, 'direct', 'tenant-two-stage', 'Tenant two stage', 10, 0, 'ACTIVE')");
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "INSERT INTO crm_opportunity (tenant_id, owner_actor_id, customer_id, name, "
                            + "pipeline_code, stage_code, outcome, status) "
                            + "VALUES (91, 91, 1, 'cross tenant pair', 'direct', 'tenant-two-stage', "
                            + "'OPEN', 'ACTIVE')"));
        }
    }

    @Test
    void customerTagKeyMigrationBackfillsNfkcKeysAndCollapsesAliases() {
        try (MigrationTestDatabase upgradeDatabase = MigrationTestDatabase.create()) {
            Flyway.configure()
                    .dataSource(upgradeDatabase.getJdbcUrl(), upgradeDatabase.getUsername(),
                            upgradeDatabase.getPassword())
                    .locations("classpath:db/migration")
                    .target("11")
                    .load()
                    .migrate();

            JdbcTemplate upgradeJdbc = new JdbcTemplate(new DriverManagerDataSource(
                    upgradeDatabase.getJdbcUrl(), upgradeDatabase.getUsername(),
                    upgradeDatabase.getPassword()));
            upgradeJdbc.update("INSERT INTO crm_customer (id, tenant_id, name, status) "
                    + "VALUES (101, 1, 'First', 'IN_POOL'), (102, 1, 'Second', 'IN_POOL')");
            upgradeJdbc.update("INSERT INTO crm_customer_tag (tenant_id, customer_id, tag_name) VALUES "
                    + "(1, 101, 'ﬃ'), (1, 101, 'ffi'), (1, 101, 'VIP'), "
                    + "(1, 102, 'vip'), (1, 102, 'Ａ'), (1, 102, 'a')");

            Flyway.configure()
                    .dataSource(upgradeDatabase.getJdbcUrl(), upgradeDatabase.getUsername(),
                            upgradeDatabase.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            assertEquals(4, upgradeJdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_customer_tag", Integer.class));
            assertEquals(1, upgradeJdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_customer_tag "
                            + "WHERE customer_id = 101 AND tag_key = 'ffi'",
                    Integer.class));
            assertEquals(3, upgradeJdbc.queryForObject(
                    "SELECT COUNT(DISTINCT tag_key) FROM crm_customer_tag", Integer.class));
            assertEquals(List.of("a", "ffi", "vip"), upgradeJdbc.queryForList(
                    "SELECT tag_key FROM crm_customer_tag GROUP BY tag_key ORDER BY tag_key",
                    String.class));
        }
    }

    @Test
    void contactTagKeyMigrationBackfillsNfkcKeysAndCollapsesAliases() {
        try (MigrationTestDatabase upgradeDatabase = MigrationTestDatabase.create()) {
            migrationFlyway(upgradeDatabase, "18").migrate();

            JdbcTemplate upgradeJdbc = upgradeJdbc(upgradeDatabase);
            upgradeJdbc.update("INSERT INTO crm_contact (id, tenant_id, name, status) "
                    + "VALUES (101, 1, 'First', 'IN_POOL'), (102, 1, 'Second', 'IN_POOL')");
            upgradeJdbc.update("INSERT INTO crm_contact_tag (tenant_id, contact_id, tag_name) VALUES "
                    + "(1, 101, 'ﬃ'), (1, 101, 'ffi'), (1, 101, 'VIP'), "
                    + "(1, 102, 'vip'), (1, 102, 'Ａ'), (1, 102, 'a')");

            migrationFlyway(upgradeDatabase, null).migrate();

            assertEquals(4, upgradeJdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_contact_tag", Integer.class));
            assertEquals(1, upgradeJdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_contact_tag "
                            + "WHERE contact_id = 101 AND tag_key = 'ffi'",
                    Integer.class));
            assertEquals(List.of("a", "ffi", "vip"), upgradeJdbc.queryForList(
                    "SELECT tag_key FROM crm_contact_tag GROUP BY tag_key ORDER BY tag_key",
                    String.class));
            assertEquals(Integer.valueOf(19_001), upgradeJdbc.queryForObject(
                    "SELECT checksum FROM flyway_schema_history "
                            + "WHERE version = '19' AND success = 1",
                    Integer.class));
        }
    }

    @Test
    void contactTagKeyMigrationResumesAfterRepairingFailedBackfill() {
        try (MigrationTestDatabase upgradeDatabase = MigrationTestDatabase.create()) {
            migrationFlyway(upgradeDatabase, "18").migrate();

            JdbcTemplate upgradeJdbc = upgradeJdbc(upgradeDatabase);
            upgradeJdbc.update("INSERT INTO crm_contact (id, tenant_id, name, status) "
                    + "VALUES (101, 1, 'Retry', 'IN_POOL')");
            upgradeJdbc.update("INSERT INTO crm_contact_tag (tenant_id, contact_id, tag_name) VALUES "
                    + "(1, 101, 'Alpha'), (1, 101, 'ﬃ'), (1, 101, 'ffi'), (1, 101, '　')");

            Flyway migration = migrationFlyway(upgradeDatabase, "19");
            assertThrows(FlywayException.class, migration::migrate);
            assertEquals(1, upgradeJdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'crm_contact_tag' "
                            + "AND column_name = 'tag_key'",
                    Integer.class));
            assertEquals("alpha", upgradeJdbc.queryForObject(
                    "SELECT tag_key FROM crm_contact_tag "
                            + "WHERE tenant_id = 1 AND contact_id = 101 AND tag_name = 'Alpha'",
                    String.class));

            upgradeJdbc.update("DELETE FROM crm_contact_tag "
                    + "WHERE tenant_id = 1 AND contact_id = 101 AND tag_name = '　'");
            migration.repair();
            migration.migrate();

            assertEquals(2, upgradeJdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_contact_tag", Integer.class));
            assertEquals(List.of("alpha", "ffi"), upgradeJdbc.queryForList(
                    "SELECT tag_key FROM crm_contact_tag ORDER BY tag_key", String.class));
            assertEquals(List.of("tenant_id", "contact_id", "tag_key"),
                    upgradeJdbc.queryForList("SELECT column_name "
                                    + "FROM information_schema.key_column_usage "
                                    + "WHERE table_schema = DATABASE() AND table_name = 'crm_contact_tag' "
                                    + "AND constraint_name = 'PRIMARY' ORDER BY ordinal_position",
                            String.class));
            assertEquals(List.of("tenant_id", "tag_key", "contact_id", "tag_name"),
                    upgradeJdbc.queryForList("SELECT column_name "
                                    + "FROM information_schema.statistics "
                                    + "WHERE table_schema = DATABASE() AND table_name = 'crm_contact_tag' "
                                    + "AND index_name = 'idx_crm_contact_tag_catalog' ORDER BY seq_in_index",
                            String.class));
        }
    }

    @Test
    void leadTagMigrationAcceptsNoChangeUpdatesWithAffectedRowsSemantics() {
        try (MigrationTestDatabase upgradeDatabase = MigrationTestDatabase.create()) {
            String affectedRowsUrl = upgradeDatabase.getJdbcUrl()
                    + (upgradeDatabase.getJdbcUrl().contains("?") ? "&" : "?")
                    + "useAffectedRows=true";
            Flyway.configure().dataSource(affectedRowsUrl, upgradeDatabase.getUsername(),
                            upgradeDatabase.getPassword())
                    .locations("classpath:db/migration").target("23").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    affectedRowsUrl, upgradeDatabase.getUsername(), upgradeDatabase.getPassword()));
            jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) "
                    + "VALUES (7, 'Lead owner')");
            jdbc.update("INSERT INTO organization_tenant "
                    + "(tenant_id, initialized_by_actor_id) VALUES (9, 7)");
            jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) "
                    + "VALUES (9, 7, 'ACTIVE')");
            jdbc.update("INSERT INTO crm_lead (id, tenant_id, owner_actor_id, name, status) "
                    + "VALUES (101, 9, 7, 'Affected rows lead', 'ACTIVE')");
            jdbc.update("INSERT INTO crm_lead_tag (tenant_id, lead_id, tag_name) "
                    + "VALUES (9, 101, 'Alpha')");
            jdbc.execute("ALTER TABLE crm_lead_tag ADD COLUMN tag_key VARCHAR(150) NULL "
                    + "AFTER tag_name");
            jdbc.update("UPDATE crm_lead_tag SET tag_key = 'alpha' "
                    + "WHERE tenant_id = 9 AND lead_id = 101");

            assertDoesNotThrow(() -> Flyway.configure()
                    .dataSource(affectedRowsUrl, upgradeDatabase.getUsername(),
                            upgradeDatabase.getPassword())
                    .locations("classpath:db/migration").target("24").load().migrate());
            assertEquals("alpha", jdbc.queryForObject(
                    "SELECT tag_key FROM crm_lead_tag WHERE tenant_id = 9 AND lead_id = 101",
                    String.class));
        }
    }

    @Test
    void marketingFormSubmissionMigrationUpgradesV48ToV50WithTenantConstraints() {
        try (MigrationTestDatabase upgradeDatabase = MigrationTestDatabase.create()) {
            migrationFlyway(upgradeDatabase, "48").migrate();
            JdbcTemplate jdbc = upgradeJdbc(upgradeDatabase);
            jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) "
                    + "VALUES (7, 'Tenant one owner'), (8, 'Tenant two owner')");
            jdbc.update("INSERT INTO organization_tenant "
                    + "(tenant_id, initialized_by_actor_id) VALUES (1, 7), (2, 8)");
            jdbc.update("INSERT INTO organization_membership "
                    + "(tenant_id, actor_id, status) VALUES (1, 7, 'ACTIVE'), "
                    + "(2, 8, 'ACTIVE')");
            jdbc.update("INSERT INTO crm_marketing_form "
                    + "(id, tenant_id, created_by_actor_id, owner_actor_id, public_key, "
                    + "name, title, submit_button_text, success_message, status, version) "
                    + "VALUES (101, 1, 7, 7, 'abcdefghijklmnopqrstuvwx', "
                    + "'Upgrade form', 'Upgrade form', 'Submit', 'Accepted', 'ACTIVE', 3)");
            jdbc.update("INSERT INTO crm_lead "
                    + "(id, tenant_id, owner_actor_id, name, status) VALUES "
                    + "(201, 1, 7, 'Tenant one lead', 'ACTIVE'), "
                    + "(203, 1, 7, 'Tenant one invalid lead', 'ACTIVE'), "
                    + "(202, 2, 8, 'Tenant two lead', 'ACTIVE')");

            migrationFlyway(upgradeDatabase, null).migrate();

            jdbc.update("INSERT INTO crm_marketing_form_submission "
                    + "(tenant_id, marketing_form_id, form_version, lead_id, idempotency_key, "
                    + "submission_digest, submitted_time) "
                    + "VALUES (1, 101, 3, 201, 'upgrade-check-1', "
                    + "'0000000000000000000000000000000000000000000000000000000000000000', "
                    + "'2026-07-18 08:30:00.000000')");
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_marketing_form_submission "
                            + "WHERE tenant_id = 1 AND marketing_form_id = 101 "
                            + "AND form_version = 3 AND lead_id = 201",
                    Integer.class));
            assertThrows(DataAccessException.class,
                    () -> jdbc.update("INSERT INTO crm_marketing_form_submission "
                            + "(tenant_id, marketing_form_id, form_version, lead_id) "
                            + "VALUES (1, 101, 3, 201)"));
            assertThrows(DataAccessException.class,
                    () -> jdbc.update("INSERT INTO crm_marketing_form_submission "
                            + "(tenant_id, marketing_form_id, form_version, lead_id) "
                            + "VALUES (2, 101, 3, 202)"));
            assertThrows(DataAccessException.class,
                    () -> jdbc.update("INSERT INTO crm_marketing_form_submission "
                            + "(tenant_id, marketing_form_id, form_version, lead_id) "
                            + "VALUES (1, 101, -1, 203)"));
        }
    }

    @Test
    void workRecurrenceMigrationLeavesV1AutomationFenceForReconciliation() {
        try (MigrationTestDatabase upgradeDatabase = MigrationTestDatabase.create()) {
            migrationFlyway(upgradeDatabase, "66").migrate();
            JdbcTemplate jdbc = upgradeJdbc(upgradeDatabase);
            jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (7, 'Owner')");
            jdbc.update("INSERT INTO organization_tenant (tenant_id, initialized_by_actor_id) VALUES (1, 7)");
            LocalDateTime dueAt = LocalDateTime.of(2026, 7, 22, 9, 0);
            jdbc.update("INSERT INTO crm_automation_schedule "
                            + "(tenant_id, kind, status, generation, cadence_revision, next_due_at) "
                            + "VALUES (1, 'CUSTOMER_POOL_RELEASE', 'ACTIVE', 1, 'B26A_V1', ?)", dueAt);
            jdbc.update("INSERT INTO crm_automation_run "
                            + "(tenant_id, kind, generation, due_at, evaluation_time, status, available_at, attempt, "
                            + "lease_owner, lease_expires_at, lease_fence, deadline_at) "
                            + "VALUES (1, 'CUSTOMER_POOL_RELEASE', 1, ?, ?, 'LEASED', ?, 1, ?, ?, 1, ?)",
                    dueAt, dueAt, dueAt, "07ec5a52-ca1d-4d9f-aece-1bb14afc62bd", dueAt.plusMinutes(2),
                    dueAt.plusHours(24));

            migrationFlyway(upgradeDatabase, null).migrate();

            assertEquals("B26A_V1", jdbc.queryForObject(
                    "SELECT cadence_revision FROM crm_automation_control WHERE control_key = 'POOL_CADENCE'",
                    String.class));
            assertEquals("B26A_V1", jdbc.queryForObject(
                    "SELECT cadence_revision FROM crm_automation_schedule "
                            + "WHERE tenant_id = 1 AND kind = 'CUSTOMER_POOL_RELEASE'", String.class));
            assertEquals(1L, jdbc.queryForObject(
                    "SELECT generation FROM crm_automation_schedule "
                            + "WHERE tenant_id = 1 AND kind = 'CUSTOMER_POOL_RELEASE'", Long.class));
            assertEquals("LEASED", jdbc.queryForObject(
                    "SELECT status FROM crm_automation_run WHERE tenant_id = 1 "
                            + "AND kind = 'CUSTOMER_POOL_RELEASE' AND generation = 1", String.class));
        }
    }

    @Test
    void roundTripsFieldsAndIsolatesTenants() {
        TenantId tenantA = new TenantId(1L);
        TenantId tenantB = new TenantId(2L);
        User userA = newUser(tenantA, "alice", "alice@example.com");

        userRepository.save(tenantA, userA);

        assertNotNull(userA.getId());
        assertEquals(0L, userA.getVersion());
        // Read the row back through the adapter: presence alone would not catch a converter
        // that dropped or swapped columns on the way out.
        User restored = userRepository.findById(tenantA, userA.getId()).orElseThrow();
        assertEquals("alice", restored.getUsernameValue());
        assertEquals("alice@example.com", restored.getEmailValue());
        assertEquals("Test User", restored.getRealName());
        assertEquals(userA.getStatus(), restored.getStatus());
        assertEquals(0L, restored.getVersion());
        assertTrue(userRepository.findById(tenantB, userA.getId()).isEmpty());

        User userB = newUser(tenantB, "alice", "alice@example.com");
        assertDoesNotThrow(() -> userRepository.save(tenantB, userB));
    }

    private Flyway migrationFlyway(MigrationTestDatabase database, String target) {
        var configuration = Flyway.configure()
                .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private JdbcTemplate upgradeJdbc(MigrationTestDatabase database) {
        return new JdbcTemplate(new DriverManagerDataSource(
                database.getJdbcUrl(), database.getUsername(), database.getPassword()));
    }

    @Test
    void oaFormMigrationBackfillsExistingTenantFieldCatalog() {
        try (MigrationTestDatabase upgradeDatabase = MigrationTestDatabase.create()) {
            migrationFlyway(upgradeDatabase, "49").migrate();
            JdbcTemplate jdbc = upgradeJdbc(upgradeDatabase);
            jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) "
                    + "VALUES (71, 'Existing OA owner')");
            jdbc.update("INSERT INTO organization_tenant "
                    + "(tenant_id, initialized_by_actor_id) VALUES (81, 71)");
            jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) "
                    + "VALUES (81, 71, 'ACTIVE')");

            migrationFlyway(upgradeDatabase, null).migrate();

            assertEquals(List.of("applicant_actor", "applicant_department", "form_code",
                            "request_title", "submitted_time"),
                    jdbc.queryForList("SELECT field_code FROM custom_field_definition "
                                    + "WHERE tenant_id = 81 AND object_type = 'OA_REQUEST' "
                                    + "ORDER BY field_code",
                            String.class));
            assertEquals(2, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() "
                            + "AND table_name IN ('oa_form_definition', 'oa_form_field')",
                    Integer.class));
            assertEquals(3, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.referential_constraints "
                            + "WHERE constraint_schema = DATABASE() "
                            + "AND constraint_name IN ('fk_oa_form_definition_creator', "
                            + "'fk_oa_form_definition_approval', "
                            + "'fk_oa_form_field_definition')",
                    Integer.class));
        }
    }

    @Test
    void translatesDatabaseUniqueConstraintsToDomainConflicts() {
        TenantId tenantId = new TenantId(1L);
        userRepository.save(tenantId, newUser(tenantId, "alice", "alice@example.com"));

        UserAlreadyExistsException usernameConflict = assertThrows(
                UserAlreadyExistsException.class,
                () -> userRepository.save(
                        tenantId, newUser(tenantId, "alice", "different@example.com")));
        assertEquals("Username already exists", usernameConflict.getMessage());

        UserAlreadyExistsException emailConflict = assertThrows(
                UserAlreadyExistsException.class,
                () -> userRepository.save(
                        tenantId, newUser(tenantId, "different", "alice@example.com")));
        assertEquals("Email already exists", emailConflict.getMessage());
    }

    @Test
    void detectsOptimisticLockConflict() {
        TenantId tenantId = new TenantId(1L);
        User created = userRepository.save(
                tenantId, newUser(tenantId, "alice", "alice@example.com"));
        User first = userRepository.findById(tenantId, created.getId()).orElseThrow();
        User stale = userRepository.findById(tenantId, created.getId()).orElseThrow();

        first.lock("security review");
        userRepository.save(tenantId, first);
        assertEquals(1L, first.getVersion());

        stale.changeStatus(UserStatus.INACTIVE, "stale update");
        assertThrows(AggregateVersionConflictException.class,
                () -> userRepository.save(tenantId, stale));
    }

    @Test
    void softDeleteUsesDomainStatusOnly() {
        TenantId tenantId = new TenantId(1L);
        User user = userRepository.save(
                tenantId, newUser(tenantId, "alice", "alice@example.com"));

        user.delete();
        userRepository.save(tenantId, user);

        User deleted = userRepository.findById(tenantId, new UserId(user.getId().getValue()))
                .orElseThrow();
        assertTrue(deleted.isDeleted());
        assertTrue(userRepository.findUsers(tenantId, null, null, null, 1, 20)
                .items().isEmpty());
    }

    @Test
    void authorizationPersistenceRoundTripsGrantsAssignmentsAndPreferencesWithTenantIsolation() {
        TenantId tenantA = new TenantId(31L);
        TenantId tenantB = new TenantId(32L);
        MemberId memberId = new MemberId(71L);
        seedMembership(tenantA, memberId);

        TenantRole role = tenantRoleRepository.save(tenantA, TenantRole.createCustom(
                tenantA, "Support", "Tenant support",
                List.of(RoleGrant.withScope("users:read", DataScope.DEPARTMENT_SUBTREE),
                        RoleGrant.withoutScope("navigation:read"))));
        RoleAssignment assignment = roleAssignmentRepository.save(tenantA,
                RoleAssignment.assign(tenantA, memberId, role.getId()));
        NavigationPreference preference = navigationPreferenceRepository.save(tenantA,
                NavigationPreference.create(tenantA, memberId,
                        List.of("authorization.roles", "home"), List.of("users"),
                        Set.of("authorization.roles", "home", "users")));
        DashboardPreference dashboardPreference = dashboardPreferenceRepository.save(
                tenantA, DashboardPreference.create(tenantA, memberId,
                        DashboardTimeRange.CURRENT_QUARTER, null, null,
                        DashboardDataScope.DEPARTMENT_SUBTREE, Set.of()));

        TenantRole restored = tenantRoleRepository.findById(tenantA, role.getId()).orElseThrow();
        assertEquals(2, restored.getGrants().size());
        PageResult<TenantRole> rolePage = tenantRoleRepository.findPage(tenantA, null, "Supp", 1, 1);
        assertEquals(1, rolePage.total());
        assertEquals(role.getId(), rolePage.items().getFirst().getId());
        assertEquals(assignment.getId(), roleAssignmentRepository.findByMember(tenantA, memberId)
                .getFirst().getId());
        assertEquals(1, roleAssignmentRepository.findByMembers(tenantA, List.of(memberId)).size());
        assertEquals(1, roleAssignmentRepository.findByRole(tenantA, role.getId(), 1, 20).total());
        assertEquals(1, roleAssignmentRepository.countByRole(tenantA, role.getId()));
        assertEquals(1, roleAssignmentRepository.countActiveMembersByRole(tenantA, role.getId()));
        assertEquals(List.of(memberId), roleAssignmentRepository.findActiveMembersByRoles(
                tenantA, List.of(role.getId()), 101));
        TenantRole secondRole = tenantRoleRepository.save(tenantA,
                TenantRole.createCustom(tenantA, "Escalation", null, List.of()));
        roleAssignmentRepository.save(tenantA,
                RoleAssignment.assign(tenantA, memberId, secondRole.getId()));
        assertEquals(List.of(memberId), roleAssignmentRepository.findActiveMembersByRoles(
                tenantA, List.of(role.getId(), secondRole.getId()), 101));
        NavigationPreference restoredPreference = navigationPreferenceRepository
                .findByMember(tenantA, memberId).orElseThrow();
        assertEquals(preference.getOrderedCodes(), restoredPreference.getOrderedCodes());
        restoredPreference.change(List.of("home"), List.of("users"),
                Set.of("authorization.roles", "home", "users"));
        navigationPreferenceRepository.save(tenantA, restoredPreference);
        assertEquals(1L, restoredPreference.getVersion());
        DashboardPreference restoredDashboardPreference = dashboardPreferenceRepository
                .findByMember(tenantA, memberId).orElseThrow();
        assertEquals(dashboardPreference.getDataScope(),
                restoredDashboardPreference.getDataScope());
        restoredDashboardPreference.change(DashboardTimeRange.CUSTOM,
                java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 12, 31),
                DashboardDataScope.TENANT, Set.of());
        dashboardPreferenceRepository.save(tenantA, restoredDashboardPreference);
        assertEquals(1L, restoredDashboardPreference.getVersion());
        assertTrue(tenantRoleRepository.findById(tenantB, role.getId()).isEmpty());
        assertTrue(roleAssignmentRepository.findByMember(tenantB, memberId).isEmpty());
        assertTrue(navigationPreferenceRepository.findByMember(tenantB, memberId).isEmpty());
        assertTrue(dashboardPreferenceRepository.findByMember(tenantB, memberId).isEmpty());
        new JdbcTemplate(dataSource).update(
                "UPDATE organization_membership SET status = 'SUSPENDED' "
                        + "WHERE tenant_id = ? AND actor_id = ?",
                tenantA.getValue(), memberId.getValue());
        assertTrue(roleAssignmentRepository.findActiveMembersByRoles(
                tenantA, List.of(role.getId()), 101).isEmpty());
    }

    @Test
    void authorizationRoleUsesOptimisticLockingAndCompositeForeignKeysRejectCrossTenantLinks() {
        TenantId tenantA = new TenantId(41L);
        TenantId tenantB = new TenantId(42L);
        MemberId memberA = new MemberId(81L);
        MemberId memberB = new MemberId(82L);
        seedMembership(tenantA, memberA);
        seedMembership(tenantB, memberB);
        TenantRole created = tenantRoleRepository.save(tenantB,
                TenantRole.createCustom(tenantB, "Reviewers", null, List.of()));
        TenantRole first = tenantRoleRepository.findById(tenantB, created.getId()).orElseThrow();
        TenantRole stale = tenantRoleRepository.findById(tenantB, created.getId()).orElseThrow();

        first.update("Senior Reviewers", null);
        tenantRoleRepository.save(tenantB, first);
        stale.update("Stale Reviewers", null);

        assertThrows(AggregateVersionConflictException.class,
                () -> tenantRoleRepository.save(tenantB, stale));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO authorization_role_assignment (tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                tenantA.getValue(), memberA.getValue(), created.getId().getValue()));
    }

    private User newUser(TenantId tenantId, String username, String email) {
        return User.createWithPasswordHash(
                tenantId,
                new Username(username),
                new Email(email),
                PasswordHash.fromTrustedHash("encrypted-password-hash"),
                "Test User");
    }

    private void seedLegacyOpportunity(JdbcTemplate jdbc, String pipelineCode, String stageCode) {
        seedLegacyTenant(jdbc, 91, 91, 1, "Legacy opportunity owner", "Legacy opportunity customer");
        jdbc.update("INSERT INTO crm_opportunity (tenant_id, owner_actor_id, customer_id, name, "
                        + "pipeline_code, stage_code, outcome, status) "
                + "VALUES (91, 91, 1, 'legacy invalid pipeline', ?, ?, 'OPEN', 'ACTIVE')",
                pipelineCode, stageCode);
    }

    private void seedV84PipelineUpgradeFixtures(JdbcTemplate jdbc) {
        seedLegacyTenant(jdbc, 91, 91, 1, "Legacy opportunity owner", "Legacy opportunity customer");
        jdbc.update("INSERT INTO crm_opportunity "
                        + "(id, tenant_id, owner_actor_id, customer_id, name, pipeline_code, stage_code, "
                        + "outcome, status, version) "
                        + "VALUES (101, 91, 91, 1, 'legacy upper pipeline', 'Direct', 'Qualification', "
                        + "'OPEN', 'ACTIVE', 17), "
                        + "(102, 91, 91, 1, 'legacy lower pipeline', 'direct', 'qualification', "
                        + "'OPEN', 'ACTIVE', 18)");
        jdbc.update("INSERT INTO saved_view "
                        + "(id, tenant_id, actor_id, object_type, name, conditions_json, version) "
                        + "VALUES (101, 91, 91, 'OPPORTUNITY', 'Pipeline snapshot', "
                        + "JSON_ARRAY(JSON_OBJECT('fieldCode', 'pipeline_code', 'operator', 'EQ', "
                        + "'values', JSON_ARRAY('Direct'))), 7)");
        jdbc.update("INSERT INTO report_definition "
                        + "(id, tenant_id, creator_member_id, name, normalized_name, object_type, "
                        + "time_field_code, granularity, filters_json, measures_json, version) "
                        + "VALUES (201, 91, 91, 'Pipeline report', 'pipeline report', 'OPPORTUNITY', "
                        + "'created_time', 'MONTH', JSON_ARRAY(JSON_OBJECT('fieldCode', 'pipeline_code', "
                        + "'operator', 'EQ', 'values', JSON_ARRAY('Direct'))), "
                        + "JSON_ARRAY(JSON_OBJECT('fieldCode', 'estimated_amount', 'aggregation', 'SUM')), 8)");
        jdbc.update("INSERT INTO approval_definition "
                        + "(id, tenant_id, object_type, name, normalized_name, config_json, status, version) "
                        + "VALUES (301, 91, 'OPPORTUNITY', 'Pipeline approval', 'pipeline approval', "
                        + "JSON_ARRAY(), 'INACTIVE', 4)");
        jdbc.update("INSERT INTO approval_instance "
                        + "(id, tenant_id, definition_id, definition_version, object_type, subject_id, "
                        + "idempotency_key, title, applicant_actor_id, repeat_mode, status, facts_json, "
                        + "submitted_time, completed_time, version) "
                        + "VALUES (302, 91, 301, 4, 'OPPORTUNITY', 101, 'pipeline-snapshot-302', "
                        + "'Pipeline approval instance', 91, 'ONCE', 'APPROVED', "
                        + "JSON_OBJECT('pipelineCode', 'Direct', 'stageCode', 'Qualification'), "
                        + "CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 9)");
        jdbc.update("INSERT INTO stage_workflow_definition "
                        + "(id, tenant_id, object_type, name, normalized_name, success_name, failure_name, "
                        + "config_json, status, version) "
                        + "VALUES (401, 91, 'OPPORTUNITY', 'Pipeline workflow', 'pipeline workflow', "
                        + "'Won', 'Lost', JSON_ARRAY(), 'ACTIVE', 5)");
        jdbc.update("INSERT INTO stage_workflow_record "
                        + "(id, tenant_id, workflow_id, workflow_version, object_type, subject_id, "
                        + "current_stage_code, status, progress_json, version) "
                        + "VALUES (401, 91, 401, 5, 'OPPORTUNITY', 101, 'review', 'ACTIVE', "
                        + "JSON_OBJECT('pipelineCode', 'Direct', 'stageCode', 'Qualification'), 10)");
    }

    private void seedLegacyTenant(
            JdbcTemplate jdbc, long tenantId, long actorId, long customerId,
            String ownerName, String customerName) {
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                actorId, ownerName);
        jdbc.update("INSERT INTO organization_tenant (tenant_id, initialized_by_actor_id) VALUES (?, ?)",
                tenantId, actorId);
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) "
                        + "VALUES (?, ?, 'ACTIVE')",
                tenantId, actorId);
        jdbc.update("INSERT INTO crm_customer "
                        + "(id, tenant_id, owner_actor_id, name, status, deal_status) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', 'OPEN')",
                customerId, tenantId, actorId, customerName);
    }

    private void seedMembership(TenantId tenantId, MemberId memberId) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                memberId.getValue(), "Member " + memberId.getValue());
        jdbc.update("INSERT INTO organization_tenant (tenant_id, initialized_by_actor_id) VALUES (?, ?)",
                tenantId.getValue(), memberId.getValue());
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')",
                tenantId.getValue(), memberId.getValue());
    }

    @Override
    protected void initTestData() {
        truncateTable("t_user");
        clearAuthorizationData();
    }

    @Override
    protected void clearTestData() {
        truncateTable("t_user");
        clearAuthorizationData();
    }

    private void clearAuthorizationData() {
        executeSql("DELETE FROM crm_customer_pool_automation_cursor");
        executeSql("DELETE FROM dashboard_preference");
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
