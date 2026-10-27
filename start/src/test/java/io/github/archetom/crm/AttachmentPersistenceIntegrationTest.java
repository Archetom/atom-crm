package io.github.archetom.crm;

import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Empty-schema Flyway smoke check for the V59 core, V60 typed parents and V61 paused jobs. */
@RequiresDatabase
class AttachmentPersistenceIntegrationTest extends BaseIntegrationTest {
    private static final long USAGE_TENANT = 9_240_001L;
    private static final long FILE_FIELD_TENANT = 9_240_300L;

    @Autowired
    private AttachmentWorkStore attachmentWorkStore;

    @Test
    void v59CreatesAllAttachmentFoundationTables() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer tables = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name IN (
                    'crm_attachment_file', 'crm_attachment_relation', 'crm_attachment_content',
                    'crm_attachment_job', 'crm_attachment_read_lease', 'crm_attachment_tenant_usage',
                    'crm_attachment_global_usage')
                """, Integer.class);
        assertEquals(7, tables);
    }

    @Test
    void v60AddsTypedCrmParentsAndV61AddsTheDurablePausedJobState() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history"
                + " WHERE version = '59' AND success = 1", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history"
                + " WHERE version = '60' AND success = 1", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history"
                + " WHERE version = '61' AND success = 1", Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'crm_attachment_job'
                  AND column_name IN ('recovery_source_kind', 'recovery_source_job_id')
                """, Integer.class));
        assertEquals(8, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'crm_attachment_relation'
                  AND column_name IN ('customer_id', 'contact_id', 'lead_id', 'opportunity_id',
                                      'contract_id', 'invoice_id', 'receivable_id', 'receivable_plan_id')
                """, Integer.class));
        assertEquals(8, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'crm_attachment_relation'
                  AND constraint_name IN ('fk_crm_attachment_relation_customer',
                                          'fk_crm_attachment_relation_contact',
                                          'fk_crm_attachment_relation_lead',
                                          'fk_crm_attachment_relation_opportunity',
                                          'fk_crm_attachment_relation_contract',
                                          'fk_crm_attachment_relation_invoice',
                                          'fk_crm_attachment_relation_receivable',
                                          'fk_crm_attachment_relation_receivable_plan')
                """, Integer.class));
        assertEquals(8, jdbc.queryForObject("""
                SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'crm_attachment_relation'
                  AND index_name IN ('idx_crm_attachment_relation_customer',
                                     'idx_crm_attachment_relation_contact',
                                     'idx_crm_attachment_relation_lead',
                                     'idx_crm_attachment_relation_opportunity',
                                     'idx_crm_attachment_relation_contract',
                                     'idx_crm_attachment_relation_invoice',
                                     'idx_crm_attachment_relation_receivable',
                                     'idx_crm_attachment_relation_receivable_plan')
                """, Integer.class));
    }

    @Test
    void v62AndV63AllowUnboundDraftWithdrawalAndCreateEmptySnapshotMarkers() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history"
                + " WHERE version = '62' AND success = 1", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history"
                + " WHERE version = '63' AND success = 1", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history"
                + " WHERE version = '64' AND success = 1", Integer.class));
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name IN
                    ('crm_approval_instance_file_snapshot', 'crm_stage_workflow_file_snapshot')
                """, Integer.class));

        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                FILE_FIELD_TENANT, "B24c file-field owner");
        jdbc.update("INSERT INTO organization_tenant (tenant_id, initialized_by_actor_id) VALUES (?, ?)",
                FILE_FIELD_TENANT, FILE_FIELD_TENANT);
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')",
                FILE_FIELD_TENANT, FILE_FIELD_TENANT);
        jdbc.update("""
                INSERT INTO custom_field_definition
                    (tenant_id, object_type, field_code, display_name, field_type, is_builtin,
                     is_required, is_unique, is_searchable, status, display_order, options_json)
                VALUES (?, 'CUSTOMER', 'cf_b24c_evidence', 'B24c evidence', 'FILE',
                        0, 0, 0, 0, 'ACTIVE', 901, JSON_ARRAY())
                """, FILE_FIELD_TENANT);
        long fieldId = jdbc.queryForObject("SELECT id FROM custom_field_definition WHERE tenant_id = ?"
                + " AND object_type = 'CUSTOMER' AND field_code = 'cf_b24c_evidence'", Long.class,
                FILE_FIELD_TENANT);
        jdbc.update("""
                INSERT INTO crm_attachment_file
                    (tenant_id, uploader_actor_id, display_name, declared_media_type, expected_bytes,
                     byte_state, inspection_state, publication_state)
                VALUES (?, ?, 'b24c.pdf', 'application/pdf', 4, 'UPLOADING', 'NOT_RUN', 'PRIVATE')
                """, FILE_FIELD_TENANT, FILE_FIELD_TENANT);
        long fileId = jdbc.queryForObject("SELECT MAX(id) FROM crm_attachment_file WHERE tenant_id = ?",
                Long.class, FILE_FIELD_TENANT);
        jdbc.update("""
                INSERT INTO crm_file_field_draft
                    (tenant_id, uploader_actor_id, object_type, purpose_digest, deadline, state)
                VALUES (?, ?, 'CUSTOMER', UNHEX(REPEAT('00', 32)), DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR),
                        'ACTIVE')
                """, FILE_FIELD_TENANT, FILE_FIELD_TENANT);
        long draftId = jdbc.queryForObject("SELECT MAX(id) FROM crm_file_field_draft WHERE tenant_id = ?",
                Long.class, FILE_FIELD_TENANT);
        jdbc.update("""
                INSERT INTO crm_file_field_draft_file
                    (tenant_id, draft_id, object_type, field_id, file_id, ordinal, state)
                VALUES (?, ?, 'CUSTOMER', ?, ?, 0, 'STAGED')
                """, FILE_FIELD_TENANT, draftId, fieldId, fileId);

        // The STAGED predicate pins the transition itself: an unconditional update would also
        // report one row after the candidate had already left STAGED.
        assertEquals(1, jdbc.update("UPDATE crm_file_field_draft_file SET state = 'WITHDRAWN'"
                + " WHERE tenant_id = ? AND draft_id = ? AND state = 'STAGED'",
                FILE_FIELD_TENANT, draftId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_file_field_draft_file"
                + " WHERE tenant_id = ? AND draft_id = ? AND state = 'WITHDRAWN'"
                + " AND content_version IS NULL AND evidence_digest IS NULL", Integer.class,
                FILE_FIELD_TENANT, draftId));
    }

    @Test
    void existingV58SchemaUpgradesThroughV61TypedAttachmentAndPauseMigrations() {
        try (MySQLContainer upgradeDatabase = new MySQLContainer(DockerImageName.parse("mysql:9.7.1"))
                .withDatabaseName("attachment_v58_upgrade")
                .withUsername("upgrade_user")
                .withPassword("upgrade_password")
                .withStartupTimeout(Duration.ofMinutes(2))) {
            upgradeDatabase.start();
            Flyway.configure().dataSource(upgradeDatabase.getJdbcUrl(), upgradeDatabase.getUsername(),
                    upgradeDatabase.getPassword()).locations("classpath:db/migration").target("58").load().migrate();
            JdbcTemplate upgradeJdbc = new JdbcTemplate(new DriverManagerDataSource(upgradeDatabase.getJdbcUrl(),
                    upgradeDatabase.getUsername(), upgradeDatabase.getPassword()));
            assertEquals(0, upgradeJdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables"
                    + " WHERE table_schema = DATABASE() AND table_name = 'crm_attachment_job'", Integer.class));

            Flyway.configure().dataSource(upgradeDatabase.getJdbcUrl(), upgradeDatabase.getUsername(),
                    upgradeDatabase.getPassword()).locations("classpath:db/migration").load().migrate();

            assertEquals(1, upgradeJdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history"
                    + " WHERE version = '59' AND success = 1", Integer.class));
            assertEquals(1, upgradeJdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history"
                    + " WHERE version = '60' AND success = 1", Integer.class));
            assertEquals(1, upgradeJdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history"
                    + " WHERE version = '61' AND success = 1", Integer.class));
            assertEquals(7, upgradeJdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema = DATABASE() AND table_name IN (
                        'crm_attachment_file', 'crm_attachment_relation', 'crm_attachment_content',
                        'crm_attachment_job', 'crm_attachment_read_lease', 'crm_attachment_tenant_usage',
                    'crm_attachment_global_usage')
                    """, Integer.class));
            assertEquals(8, upgradeJdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'crm_attachment_relation'
                      AND column_name IN ('customer_id', 'contact_id', 'lead_id', 'opportunity_id',
                                          'contract_id', 'invoice_id', 'receivable_id', 'receivable_plan_id')
                    """, Integer.class));
        }
    }

    @Test
    void usageReservationUsesTenantRowCheckedArithmeticAndLeavesRejectedReservationUntouched() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                USAGE_TENANT, "Attachment usage test");
        jdbc.update("INSERT INTO organization_tenant (tenant_id, initialized_by_actor_id) VALUES (?, ?)",
                USAGE_TENANT, USAGE_TENANT);
        TenantId tenant = new TenantId(USAGE_TENANT);
        AttachmentWorkStore.UsageLimits limits = new AttachmentWorkStore.UsageLimits(
                32, 32, 2, 2, 2, 2, 20, 0);

        org.junit.jupiter.api.Assertions.assertTrue(attachmentWorkStore.reserveUsage(tenant,
                new AttachmentWorkStore.UsageDelta(0, 0, 10, 1, 1, 0, 0), limits, Instant.now()));
        org.junit.jupiter.api.Assertions.assertFalse(attachmentWorkStore.reserveUsage(tenant,
                new AttachmentWorkStore.UsageDelta(0, 0, 11, 1, 1, 0, 0), limits, Instant.now()));

        Long pending = jdbc.queryForObject("SELECT pending_bytes FROM crm_attachment_tenant_usage WHERE tenant_id = ?",
                Long.class, USAGE_TENANT);
        Long uploads = jdbc.queryForObject("SELECT active_uploads FROM crm_attachment_tenant_usage WHERE tenant_id = ?",
                Long.class, USAGE_TENANT);
        assertEquals(10L, pending);
        assertEquals(1L, uploads);
        attachmentWorkStore.adjustUsage(tenant,
                new AttachmentWorkStore.UsageDelta(0, 0, -10, -1, -1, 0, 0), Instant.now());
    }

    @Test
    void globalReserveIsSharedAcrossTenants() {
        long secondTenantId = USAGE_TENANT + 1;
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                USAGE_TENANT, "Attachment global usage first tenant");
        jdbc.update("INSERT INTO organization_tenant (tenant_id, initialized_by_actor_id) VALUES (?, ?)",
                USAGE_TENANT, USAGE_TENANT);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                secondTenantId, "Attachment global usage test");
        jdbc.update("INSERT INTO organization_tenant (tenant_id, initialized_by_actor_id) VALUES (?, ?)",
                secondTenantId, secondTenantId);
        AttachmentWorkStore.UsageLimits limits = new AttachmentWorkStore.UsageLimits(
                100, 100, 10, 10, 10, 10, 20, 0);
        Instant now = Instant.now();
        org.junit.jupiter.api.Assertions.assertTrue(attachmentWorkStore.reserveUsage(new TenantId(USAGE_TENANT),
                new AttachmentWorkStore.UsageDelta(0, 0, 12, 1, 1, 0, 0), limits, now));
        org.junit.jupiter.api.Assertions.assertFalse(attachmentWorkStore.reserveUsage(new TenantId(secondTenantId),
                new AttachmentWorkStore.UsageDelta(0, 0, 9, 1, 1, 0, 0), limits, now));
        attachmentWorkStore.adjustUsage(new TenantId(USAGE_TENANT),
                new AttachmentWorkStore.UsageDelta(0, 0, -12, -1, -1, 0, 0), now);
    }

    @Test
    void exhaustedJobAtomicallyCreatesTerminalRecoveryAndExpiredLeaseCanBeTakenOver() {
        Fixture fixture = fixture(USAGE_TENANT + 100);
        Instant now = Instant.now();
        attachmentWorkStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(fixture.tenant(), fixture.fileId(), 1,
                AttachmentWorkStore.JobKind.INSPECTION, 4, now.minusSeconds(1), now.plusSeconds(60)));
        List<AttachmentWorkStore.LeasedJob> first = attachmentWorkStore.claimDueJobs(AttachmentWorkStore.JobKind.INSPECTION,
                new byte[] {1}, now, now.plusSeconds(10), 10, 1, now.plusSeconds(30));
        assertEquals(1, first.size());
        assertFalse(first.getFirst().terminal());
        assertEquals(AttachmentWorkStore.JobRelease.DEAD, attachmentWorkStore.releaseJobForRetry(first.getFirst().fence(),
                AttachmentWorkStore.JobKind.INSPECTION, now.plusSeconds(5), "TEST", now, 1, now.plusSeconds(30)));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals("DEAD", jdbc.queryForObject("SELECT state FROM crm_attachment_job WHERE id = ?", String.class,
                first.getFirst().fence().jobId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_attachment_job WHERE tenant_id = ? AND file_id = ?"
                        + " AND kind = 'RETENTION_SWEEP' AND generation = ? AND state = 'PENDING'", Integer.class,
                fixture.tenant().getValue(), fixture.fileId().getValue(), AttachmentWorkStore.terminalRecoveryGeneration(
                        AttachmentWorkStore.JobKind.INSPECTION, first.getFirst().fence().jobId())));

        attachmentWorkStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(fixture.tenant(), fixture.fileId(), 1,
                AttachmentWorkStore.JobKind.PUBLICATION, 5, now.minusSeconds(1), now.plusSeconds(60)));
        AttachmentWorkStore.LeasedJob leased = attachmentWorkStore.claimDueJobs(AttachmentWorkStore.JobKind.PUBLICATION,
                new byte[] {2}, now, now.plusSeconds(10), 10, 3, now.plusSeconds(30)).getFirst();
        jdbc.update("UPDATE crm_attachment_job SET lease_until = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND) WHERE id = ?",
                leased.fence().jobId());
        AttachmentWorkStore.LeasedJob takeover = attachmentWorkStore.claimDueJobs(AttachmentWorkStore.JobKind.PUBLICATION,
                new byte[] {3}, Instant.now(), Instant.now().plusSeconds(10), 10, 3, Instant.now().plusSeconds(30)).getFirst();
        assertTrue(takeover.fence().leaseFence() > leased.fence().leaseFence());
    }

    @Test
    void shortUploadIntentTtlDoesNotMakeDraftRetentionTerminalBeforeItBecomesDue() {
        Fixture fixture = fixture(USAGE_TENANT + 175);
        Instant created = Instant.parse("2026-07-23T00:00:00Z");
        Instant shortIntentDeadline = created.plusSeconds(30);
        Instant draftDeadline = created.plus(Duration.ofMinutes(30));
        Instant tombstoneDeadline = draftDeadline.plusSeconds(8);

        // This mirrors public MarketingForm FILE scheduling: the intent may expire in seconds,
        // but the retention worker is not due until the 30-minute draft deadline.
        attachmentWorkStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(
                fixture.tenant(), fixture.fileId(), 1, AttachmentWorkStore.JobKind.RETENTION_SWEEP,
                1, draftDeadline, tombstoneDeadline));
        // At the expired intent deadline the retention job is not due yet, so nothing is claimable.
        assertTrue(attachmentWorkStore.claimDueJobs(
                AttachmentWorkStore.JobKind.RETENTION_SWEEP, new byte[] {7}, shortIntentDeadline,
                shortIntentDeadline.plusSeconds(2), 1, 3, shortIntentDeadline.plusSeconds(30)).isEmpty());

        List<AttachmentWorkStore.LeasedJob> claimed = attachmentWorkStore.claimDueJobs(
                AttachmentWorkStore.JobKind.RETENTION_SWEEP, new byte[] {7}, draftDeadline,
                draftDeadline.plusSeconds(2), 1, 3, draftDeadline.plusSeconds(30));

        assertEquals(1, claimed.size());
        assertFalse(claimed.getFirst().terminal());
        assertTrue(claimed.getFirst().deadline().isAfter(draftDeadline));
    }

    @Test
    void terminalRecoveriesFromDifferentStagesNeverShareOneRetentionIdentity() {
        Fixture fixture = fixture(USAGE_TENANT + 150);
        Instant now = Instant.now();
        java.util.List<Long> recoveryGenerations = new java.util.ArrayList<>();
        for (AttachmentWorkStore.JobKind kind : java.util.List.of(AttachmentWorkStore.JobKind.PUBLICATION,
                AttachmentWorkStore.JobKind.DELETION)) {
            attachmentWorkStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(fixture.tenant(), fixture.fileId(), 1,
                    kind, 1, now.minusSeconds(1), now.plusSeconds(60)));
            AttachmentWorkStore.LeasedJob leased = attachmentWorkStore.claimDueJobs(kind, new byte[] {(byte) kind.ordinal()},
                    now, now.plusSeconds(10), 1, 1, now.plusSeconds(30)).getFirst();
            recoveryGenerations.add(AttachmentWorkStore.terminalRecoveryGeneration(kind, leased.fence().jobId()));
            assertEquals(AttachmentWorkStore.JobRelease.DEAD, attachmentWorkStore.releaseJobForRetry(leased.fence(), kind,
                    now.plusSeconds(5), "TEST", now, 1, now.plusSeconds(30)));
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM crm_attachment_job WHERE tenant_id = ? AND file_id = ?"
                        + " AND kind = 'RETENTION_SWEEP' AND state = 'PENDING'"
                        + " AND generation IN (?, ?)", Integer.class,
                fixture.tenant().getValue(), fixture.fileId().getValue(), recoveryGenerations.getFirst(),
                recoveryGenerations.getLast()));
    }

    @Test
    void rejectedReservationStillSettlesExpiredDownloadLeasesAndReleaseIsIdempotent() {
        long tenantId = USAGE_TENANT + 200;
        Fixture fixture = fixture(tenantId);
        TenantId tenant = fixture.tenant();
        Instant now = Instant.now();
        AttachmentWorkStore.UsageLimits limits = new AttachmentWorkStore.UsageLimits(
                100, 100, 10, 10, 10, 1, 100, 0);
        assertTrue(attachmentWorkStore.reserveUsage(tenant,
                new AttachmentWorkStore.UsageDelta(0, 0, 0, 0, 0, 0, 1), limits, now));
        AttachmentWorkStore.ReadLease expired = attachmentWorkStore.acquireReadLease(tenant, fixture.fileId(), 1,
                now.plusSeconds(30), now);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("UPDATE crm_attachment_read_lease SET expires_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND)"
                + " WHERE lease_id = ?", expired.leaseId());

        assertFalse(attachmentWorkStore.reserveUsage(tenant,
                new AttachmentWorkStore.UsageDelta(0, 0, 0, 0, 0, 0, 2), limits, Instant.now()));
        assertEquals(0L, jdbc.queryForObject("SELECT active_downloads FROM crm_attachment_tenant_usage WHERE tenant_id = ?",
                Long.class, tenantId));
        assertEquals("RELEASED", jdbc.queryForObject("SELECT state FROM crm_attachment_read_lease WHERE lease_id = ?",
                String.class, expired.leaseId()));
        attachmentWorkStore.releaseReadLease(expired, Instant.now());
        assertEquals(0L, jdbc.queryForObject("SELECT active_downloads FROM crm_attachment_tenant_usage WHERE tenant_id = ?",
                Long.class, tenantId));
    }

    private Fixture fixture(long tenantId) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)", tenantId, "Attachment job owner");
        jdbc.update("INSERT INTO organization_tenant (tenant_id, initialized_by_actor_id) VALUES (?, ?)", tenantId, tenantId);
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')", tenantId, tenantId);
        jdbc.update("INSERT INTO crm_work_task (tenant_id, created_by_actor_id, current_assignee_actor_id, title) VALUES (?, ?, ?, ?)",
                tenantId, tenantId, tenantId, "Attachment job task");
        jdbc.update("INSERT INTO crm_attachment_file (tenant_id, uploader_actor_id, display_name, declared_media_type, expected_bytes,"
                + " byte_state, inspection_state, publication_state) VALUES (?, ?, 'job.pdf', 'application/pdf', 4, 'UPLOADING', 'NOT_RUN', 'PRIVATE')",
                tenantId, tenantId);
        Long fileId = jdbc.queryForObject("SELECT MAX(id) FROM crm_attachment_file WHERE tenant_id = ?", Long.class, tenantId);
        return new Fixture(new TenantId(tenantId), new io.github.archetom.crm.domain.valueobject.AttachmentFileId(fileId));
    }
    private record Fixture(TenantId tenant, io.github.archetom.crm.domain.valueobject.AttachmentFileId fileId) { }

    @Override
    protected void clearTestData() {
        executeSql("DELETE FROM crm_attachment_tenant_usage WHERE tenant_id = " + USAGE_TENANT);
        executeSql("DELETE FROM crm_attachment_tenant_usage WHERE tenant_id = " + (USAGE_TENANT + 1));
        executeSql("DELETE FROM crm_attachment_global_usage WHERE singleton_id = 1");
        long jobTenant = USAGE_TENANT + 100;
        long terminalTenant = USAGE_TENANT + 150;
        long shortIntentTenant = USAGE_TENANT + 175;
        long leaseTenant = USAGE_TENANT + 200;
        executeSql("DELETE FROM crm_stage_workflow_file_fact WHERE tenant_id = " + FILE_FIELD_TENANT);
        executeSql("DELETE FROM crm_stage_workflow_file_snapshot WHERE tenant_id = " + FILE_FIELD_TENANT);
        executeSql("DELETE FROM crm_approval_instance_file_fact WHERE tenant_id = " + FILE_FIELD_TENANT);
        executeSql("DELETE FROM crm_approval_instance_file_snapshot WHERE tenant_id = " + FILE_FIELD_TENANT);
        executeSql("DELETE FROM crm_oa_request_file_value WHERE tenant_id = " + FILE_FIELD_TENANT);
        executeSql("DELETE FROM crm_attachment_field_binding WHERE tenant_id = " + FILE_FIELD_TENANT);
        executeSql("DELETE FROM crm_file_field_draft_file WHERE tenant_id = " + FILE_FIELD_TENANT);
        executeSql("DELETE FROM crm_file_field_draft WHERE tenant_id = " + FILE_FIELD_TENANT);
        executeSql("DELETE FROM crm_attachment_content WHERE tenant_id = " + FILE_FIELD_TENANT);
        executeSql("DELETE FROM crm_attachment_file WHERE tenant_id = " + FILE_FIELD_TENANT);
        executeSql("DELETE FROM custom_field_definition WHERE tenant_id = " + FILE_FIELD_TENANT);
        executeSql("DELETE FROM organization_membership WHERE tenant_id = " + FILE_FIELD_TENANT);
        executeSql("DELETE FROM organization_tenant WHERE tenant_id = " + FILE_FIELD_TENANT);
        executeSql("DELETE FROM organization_member_profile WHERE actor_id = " + FILE_FIELD_TENANT);
        executeSql("DELETE FROM crm_attachment_job WHERE tenant_id = " + jobTenant);
        executeSql("DELETE FROM crm_attachment_read_lease WHERE tenant_id = " + jobTenant);
        executeSql("DELETE FROM crm_attachment_content WHERE tenant_id = " + jobTenant);
        executeSql("DELETE FROM crm_attachment_relation WHERE tenant_id = " + jobTenant);
        executeSql("DELETE FROM crm_attachment_file WHERE tenant_id = " + jobTenant);
        executeSql("DELETE FROM crm_work_task WHERE tenant_id = " + jobTenant);
        executeSql("DELETE FROM organization_membership WHERE tenant_id = " + jobTenant);
        executeSql("DELETE FROM organization_tenant WHERE tenant_id = " + jobTenant);
        executeSql("DELETE FROM organization_member_profile WHERE actor_id = " + jobTenant);
        executeSql("DELETE FROM crm_attachment_job WHERE tenant_id = " + terminalTenant);
        executeSql("DELETE FROM crm_attachment_read_lease WHERE tenant_id = " + terminalTenant);
        executeSql("DELETE FROM crm_attachment_content WHERE tenant_id = " + terminalTenant);
        executeSql("DELETE FROM crm_attachment_relation WHERE tenant_id = " + terminalTenant);
        executeSql("DELETE FROM crm_attachment_file WHERE tenant_id = " + terminalTenant);
        executeSql("DELETE FROM crm_work_task WHERE tenant_id = " + terminalTenant);
        executeSql("DELETE FROM organization_membership WHERE tenant_id = " + terminalTenant);
        executeSql("DELETE FROM crm_attachment_tenant_usage WHERE tenant_id = " + terminalTenant);
        executeSql("DELETE FROM organization_tenant WHERE tenant_id = " + terminalTenant);
        executeSql("DELETE FROM organization_member_profile WHERE actor_id = " + terminalTenant);
        executeSql("DELETE FROM crm_attachment_job WHERE tenant_id = " + shortIntentTenant);
        executeSql("DELETE FROM crm_attachment_content WHERE tenant_id = " + shortIntentTenant);
        executeSql("DELETE FROM crm_attachment_relation WHERE tenant_id = " + shortIntentTenant);
        executeSql("DELETE FROM crm_attachment_file WHERE tenant_id = " + shortIntentTenant);
        executeSql("DELETE FROM crm_work_task WHERE tenant_id = " + shortIntentTenant);
        executeSql("DELETE FROM organization_membership WHERE tenant_id = " + shortIntentTenant);
        executeSql("DELETE FROM organization_tenant WHERE tenant_id = " + shortIntentTenant);
        executeSql("DELETE FROM organization_member_profile WHERE actor_id = " + shortIntentTenant);
        executeSql("DELETE FROM crm_attachment_read_lease WHERE tenant_id = " + leaseTenant);
        executeSql("DELETE FROM crm_attachment_job WHERE tenant_id = " + leaseTenant);
        executeSql("DELETE FROM crm_attachment_content WHERE tenant_id = " + leaseTenant);
        executeSql("DELETE FROM crm_attachment_relation WHERE tenant_id = " + leaseTenant);
        executeSql("DELETE FROM crm_attachment_file WHERE tenant_id = " + leaseTenant);
        executeSql("DELETE FROM crm_work_task WHERE tenant_id = " + leaseTenant);
        executeSql("DELETE FROM organization_membership WHERE tenant_id = " + leaseTenant);
        executeSql("DELETE FROM crm_attachment_tenant_usage WHERE tenant_id = " + leaseTenant);
        executeSql("DELETE FROM organization_tenant WHERE tenant_id = " + leaseTenant);
        executeSql("DELETE FROM organization_member_profile WHERE actor_id = " + leaseTenant);
        executeSql("DELETE FROM organization_tenant WHERE tenant_id = " + USAGE_TENANT);
        executeSql("DELETE FROM organization_tenant WHERE tenant_id = " + (USAGE_TENANT + 1));
        executeSql("DELETE FROM organization_member_profile WHERE actor_id = " + USAGE_TENANT);
        executeSql("DELETE FROM organization_member_profile WHERE actor_id = " + (USAGE_TENANT + 1));
    }
}
