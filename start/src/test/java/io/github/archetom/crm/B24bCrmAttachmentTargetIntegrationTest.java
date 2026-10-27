package io.github.archetom.crm;

import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccessRegistry;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.attachment.worker.AttachmentPublicationWorker;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AttachmentFileMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B24b acceptance coverage for the eight CRM attachment parents and their typed relation invariant. */
@RequiresDatabase
@Import(B24bCrmAttachmentTargetIntegrationTest.AttachmentPolicyConfiguration.class)
class B24bCrmAttachmentTargetIntegrationTest extends BaseIntegrationTest {
    private static final long TENANT = 9_240_500L;
    private static final long FOREIGN_TENANT = TENANT + 1;

    @Autowired
    private AttachmentTargetAccessRegistry targetAccessRegistry;

    @Autowired
    private AttachmentFileMapper attachmentFileMapper;

    @Autowired
    private AttachmentFileRepository attachmentFileRepository;

    @Autowired
    private AttachmentWorkStore attachmentWorkStore;

    @Autowired
    private AttachmentParentLifecycle attachmentParentLifecycle;

    @Autowired
    private AttachmentPublicationWorker attachmentPublicationWorker;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void registersEveryClosedAttachmentTargetIncludingAllEightCrmRoots() {
        for (AttachmentTargetType type : AttachmentTargetType.values()) {
            assertNotNull(targetAccessRegistry.require(type));
        }
    }

    @ParameterizedTest(name = "{0} has an exact typed parent lock and cannot be read through another parent")
    @MethodSource("crmTargetTypes")
    void everyCrmTargetUsesItsTypedRootAndRejectsTargetFileMismatch(AttachmentTargetType type) {
        Fixture fixture = fixture(TENANT);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        CrmParents parents = insertCrmParents(jdbc, fixture.tenantId());
        long fileId = insertAttachmentFile(jdbc, fixture.tenantId());
        long parentId = parents.id(type);
        jdbc.update("INSERT INTO crm_attachment_relation (tenant_id, file_id, target_type, " + targetColumn(type)
                        + ") VALUES (?, ?, ?, ?)", fixture.tenantId(), fileId, type.name(), parentId);
        TenantId tenantId = new TenantId(fixture.tenantId());
        AttachmentTarget target = new AttachmentTarget(type, parentId);

        assertTrue(targetAccessRegistry.require(type).lockForWorker(tenantId, target).attachmentsPermitted());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertEquals(target, attachmentFileRepository.findTargetForFile(tenantId,
                    new AttachmentFileId(fileId)).orElseThrow());
            assertTrue(attachmentFileRepository.findByTargetForUpdate(tenantId,
                    new AttachmentTarget(type, parentId + 10_000L), new AttachmentFileId(fileId)).isEmpty());
        });
    }

    @Test
    void rejectsAllEightWrongCrmParentTypesAndTheMissingWorkTaskParent() {
        Fixture fixture = fixture(TENANT);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        for (AttachmentTargetType type : List.of(AttachmentTargetType.CUSTOMER, AttachmentTargetType.CONTACT,
                AttachmentTargetType.LEAD, AttachmentTargetType.OPPORTUNITY, AttachmentTargetType.CONTRACT,
                AttachmentTargetType.INVOICE, AttachmentTargetType.RECEIVABLE,
                AttachmentTargetType.RECEIVABLE_PLAN)) {
            assertThrows(DataAccessException.class, () -> jdbc.update("""
                    INSERT INTO crm_attachment_relation (tenant_id, file_id, target_type, work_task_id)
                    VALUES (?, ?, ?, ?)
                    """, fixture.tenantId(), fixture.fileId(), type.name(), fixture.workTaskId()),
                    () -> type + " must not accept a work-task parent column");
        }
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO crm_attachment_relation (tenant_id, file_id, target_type)
                VALUES (?, ?, 'WORK_TASK')
                """, fixture.tenantId(), fixture.fileId()),
                "WORK_TASK must not accept a missing parent");
    }

    @Test
    void typedForeignKeyRejectsCrossTenantParentAndConcurrentSecondTarget() throws Exception {
        Fixture home = fixture(TENANT);
        Fixture foreign = fixture(FOREIGN_TENANT);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long foreignCustomerId = insertCustomer(jdbc, foreign.tenantId());
        long homeCustomerId = insertCustomer(jdbc, home.tenantId());

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO crm_attachment_relation (tenant_id, file_id, target_type, customer_id)
                VALUES (?, ?, 'CUSTOMER', ?)
                """, home.tenantId(), home.fileId(), foreignCustomerId),
                "the composite foreign key must not allow a parent owned by another tenant");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> workTarget = executor.submit(() -> insertRelationWhenStarted(home,
                    "WORK_TASK", "work_task_id", home.workTaskId(), ready, start));
            Future<Boolean> customerTarget = executor.submit(() -> insertRelationWhenStarted(home,
                    "CUSTOMER", "customer_id", homeCustomerId, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "both target inserts should contend together");
            start.countDown();
            assertEquals(1L, List.of(workTarget.get(10, TimeUnit.SECONDS), customerTarget.get(10, TimeUnit.SECONDS))
                    .stream().filter(Boolean::booleanValue).count(),
                    "one file may be attached to exactly one typed target");
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_attachment_relation"
                + " WHERE tenant_id = ? AND file_id = ?", Integer.class, home.tenantId(), home.fileId()));
    }

    @Test
    void typedAvailablePageExecutesItsCountAndLimitedJoinInMySql() {
        Fixture fixture = fixture(TENANT);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long customerId = insertCustomer(jdbc, fixture.tenantId());
        jdbc.update("""
                UPDATE crm_attachment_file
                   SET byte_state = 'STORED', inspection_state = 'CLEAN', publication_state = 'AVAILABLE',
                       actual_bytes = 4, media_type = 'application/pdf', available_time = UTC_TIMESTAMP(6)
                 WHERE tenant_id = ? AND id = ?
                """, fixture.tenantId(), fixture.fileId());
        jdbc.update("""
                INSERT INTO crm_attachment_relation (tenant_id, file_id, target_type, customer_id)
                VALUES (?, ?, 'CUSTOMER', ?)
                """, fixture.tenantId(), fixture.fileId(), customerId);

        assertEquals(1L, attachmentFileMapper.countAvailableByTarget(
                fixture.tenantId(), AttachmentTargetType.CUSTOMER.name(), customerId, null, null));
        assertEquals(List.of(fixture.fileId()), attachmentFileMapper.selectAvailablePageByTarget(
                        fixture.tenantId(), AttachmentTargetType.CUSTOMER.name(), customerId,
                        null, null, 0L, 1L)
                .stream().map(value -> value.getId()).toList());
    }

    @Test
    void poolPausedCustomerAndContactJobsDeferInsideTheLeaseTransactionAndRearmOnRestore() {
        Fixture customerFixture = fixture(TENANT);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long customerId = insertCustomer(jdbc, customerFixture.tenantId());
        jdbc.update("UPDATE crm_customer SET status = 'IN_POOL' WHERE tenant_id = ? AND id = ?",
                customerFixture.tenantId(), customerId);
        deferThenRestore(jdbc, customerFixture, AttachmentTargetType.CUSTOMER, customerId,
                AttachmentWorkStore.JobKind.INSPECTION);

        long contactFileId = insertAttachmentFile(jdbc, TENANT);
        Fixture contactFixture = new Fixture(TENANT, customerFixture.workTaskId(), contactFileId);
        long contactId = insertContact(jdbc, contactFixture.tenantId());
        jdbc.update("UPDATE crm_contact SET status = 'IN_POOL' WHERE tenant_id = ? AND id = ?",
                contactFixture.tenantId(), contactId);
        deferThenRestore(jdbc, contactFixture, AttachmentTargetType.CONTACT, contactId,
                AttachmentWorkStore.JobKind.PUBLICATION);
    }

    @Test
    void parentDeleteWinsThePublicationRaceAndLateWorkerCannotResurrectTheWithdrawnFile() throws Exception {
        Fixture fixture = fixture(TENANT);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long customerId = insertCustomer(jdbc, fixture.tenantId());
        jdbc.update("UPDATE crm_attachment_file SET byte_state = 'STORED', inspection_state = 'CLEAN',"
                        + " actual_bytes = 4, media_type = 'application/pdf' WHERE tenant_id = ? AND id = ?",
                fixture.tenantId(), fixture.fileId());
        jdbc.update("INSERT INTO crm_attachment_relation (tenant_id, file_id, target_type, customer_id)"
                        + " VALUES (?, ?, 'CUSTOMER', ?)", fixture.tenantId(), fixture.fileId(), customerId);
        TenantId tenantId = new TenantId(fixture.tenantId());
        AttachmentTarget target = new AttachmentTarget(AttachmentTargetType.CUSTOMER, customerId);
        Instant now = Instant.now();
        attachmentWorkStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId,
                new AttachmentFileId(fixture.fileId()), 1L, AttachmentWorkStore.JobKind.PUBLICATION, 1L,
                now, now.plusSeconds(300)));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT id FROM crm_customer WHERE tenant_id = ? AND id = ? FOR UPDATE",
                        Long.class, fixture.tenantId(), customerId);
                Future<?> worker = executor.submit(attachmentPublicationWorker::runOnce);
                jdbc.update("UPDATE crm_customer SET status = 'DELETED' WHERE tenant_id = ? AND id = ?",
                        fixture.tenantId(), customerId);
                attachmentParentLifecycle.withdrawAll(tenantId, target);
                try {
                    worker.get(100, TimeUnit.MILLISECONDS);
                } catch (java.util.concurrent.TimeoutException ignored) {
                    // The worker must wait for this parent-root transaction before publishing.
                } catch (Exception exception) {
                    throw new IllegalStateException("publication worker failed before parent delete committed", exception);
                }
            });
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertEquals("WITHDRAWN", jdbc.queryForObject("SELECT publication_state FROM crm_attachment_file"
                + " WHERE tenant_id = ? AND id = ?", String.class, fixture.tenantId(), fixture.fileId()));
        assertEquals("SETTLED", jdbc.queryForObject("SELECT state FROM crm_attachment_job"
                + " WHERE tenant_id = ? AND file_id = ? AND kind = 'PUBLICATION'", String.class,
                fixture.tenantId(), fixture.fileId()));
    }

    private void deferThenRestore(JdbcTemplate jdbc, Fixture fixture, AttachmentTargetType type, long parentId,
                                  AttachmentWorkStore.JobKind kind) {
        String parentColumn = switch (type) {
            case CUSTOMER -> "customer_id";
            case CONTACT -> "contact_id";
            default -> throw new IllegalArgumentException("pool target type is invalid");
        };
        jdbc.update("INSERT INTO crm_attachment_relation (tenant_id, file_id, target_type, " + parentColumn
                        + ") VALUES (?, ?, ?, ?)",
                fixture.tenantId(), fixture.fileId(), type.name(), parentId);
        AttachmentTarget target = new AttachmentTarget(type, parentId);
        TenantId tenantId = new TenantId(fixture.tenantId());
        assertTrue(targetAccessRegistry.require(type).lockForWorker(tenantId, target).attachmentsPaused());

        Instant now = Instant.now();
        attachmentWorkStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId,
                new AttachmentFileId(fixture.fileId()), 1L, kind, 1L, now, now.plusSeconds(300)));
        AttachmentWorkStore.LeasedJob job = attachmentWorkStore.claimDueJobs(kind, new byte[] { 7 }, now,
                now.plusSeconds(60), 1, 3, now.plusSeconds(600)).getFirst();

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(2);
        transaction.executeWithoutResult(status -> {
            assertTrue(attachmentWorkStore.assertLeaseCurrent(job.fence(), now));
            assertTrue(attachmentWorkStore.deferPausedJob(job.fence(), now),
                    "pause must update the locked lease in this transaction, without REQUIRES_NEW");
        });
        Map<String, Object> paused = jdbc.queryForMap("SELECT state, attempt, lease_owner, lease_until"
                        + " FROM crm_attachment_job WHERE id = ?", job.fence().jobId());
        assertEquals("PAUSED", paused.get("state"));
        assertEquals(0L, ((Number) paused.get("attempt")).longValue());
        assertEquals(null, paused.get("lease_owner"));
        assertEquals(null, paused.get("lease_until"));

        assertTrue(attachmentWorkStore.claimDueJobs(kind, new byte[] { 8 }, now.plusSeconds(301),
                now.plusSeconds(361), 1, 3, now.plusSeconds(900)).isEmpty(),
                "even an expired paused job must never terminal-recover while the parent is in the pool");
        assertEquals("PAUSED", jdbc.queryForObject("SELECT state FROM crm_attachment_job WHERE id = ?",
                String.class, job.fence().jobId()));

        String table = type == AttachmentTargetType.CUSTOMER ? "crm_customer" : "crm_contact";
        jdbc.update("UPDATE " + table + " SET status = 'ACTIVE', owner_actor_id = ?"
                        + " WHERE tenant_id = ? AND id = ?", fixture.tenantId(), fixture.tenantId(), parentId);
        assertTrue(targetAccessRegistry.require(type).lockForWorker(tenantId, target).attachmentsPermitted());
        attachmentParentLifecycle.resumeAll(tenantId, target);

        Instant restoredNow = Instant.now().plusSeconds(1);
        AttachmentWorkStore.LeasedJob restored = attachmentWorkStore.claimDueJobs(kind, new byte[] { 9 }, restoredNow,
                restoredNow.plusSeconds(60), 1, 3, restoredNow.plusSeconds(600)).getFirst();
        assertEquals(kind, restored.kind());
        assertFalse(restored.terminal());
        assertEquals(1L, jdbc.queryForObject("SELECT attempt FROM crm_attachment_job WHERE id = ?", Long.class,
                restored.fence().jobId()));
    }

    private boolean insertRelationWhenStarted(Fixture fixture, String targetType, String parentColumn,
                                              long parentId, CountDownLatch ready, CountDownLatch start) throws Exception {
        String sql = "INSERT INTO crm_attachment_relation (tenant_id, file_id, target_type, " + parentColumn
                + ") VALUES (?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            statement.setLong(1, fixture.tenantId());
            statement.setLong(2, fixture.fileId());
            statement.setString(3, targetType);
            statement.setLong(4, parentId);
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("target race did not start");
            try {
                statement.executeUpdate();
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                return false;
            }
        }
    }

    private Fixture fixture(long tenantId) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                tenantId, "B24b attachment target owner");
        jdbc.update("INSERT INTO organization_tenant (tenant_id, initialized_by_actor_id) VALUES (?, ?)",
                tenantId, tenantId);
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')",
                tenantId, tenantId);
        jdbc.update("INSERT INTO crm_work_task (tenant_id, created_by_actor_id, current_assignee_actor_id, title)"
                        + " VALUES (?, ?, ?, ?)", tenantId, tenantId, tenantId, "B24b attachment target task");
        jdbc.update("INSERT INTO crm_attachment_file (tenant_id, uploader_actor_id, display_name, declared_media_type,"
                        + " expected_bytes, byte_state, inspection_state, publication_state)"
                        + " VALUES (?, ?, 'target.pdf', 'application/pdf', 4, 'UPLOADING', 'NOT_RUN', 'PRIVATE')",
                tenantId, tenantId);
        Long workTaskId = jdbc.queryForObject("SELECT MAX(id) FROM crm_work_task WHERE tenant_id = ?", Long.class,
                tenantId);
        Long fileId = jdbc.queryForObject("SELECT MAX(id) FROM crm_attachment_file WHERE tenant_id = ?", Long.class,
                tenantId);
        return new Fixture(tenantId, workTaskId, fileId);
    }

    private long insertCustomer(JdbcTemplate jdbc, long tenantId) {
        jdbc.update("INSERT INTO crm_customer (tenant_id, owner_actor_id, name) VALUES (?, ?, ?)",
                tenantId, tenantId, "B24b typed attachment customer");
        return jdbc.queryForObject("SELECT MAX(id) FROM crm_customer WHERE tenant_id = ?", Long.class, tenantId);
    }

    private long insertContact(JdbcTemplate jdbc, long tenantId) {
        jdbc.update("INSERT INTO crm_contact (tenant_id, owner_actor_id, name) VALUES (?, ?, ?)",
                tenantId, tenantId, "B24b typed attachment contact");
        return jdbc.queryForObject("SELECT MAX(id) FROM crm_contact WHERE tenant_id = ?", Long.class, tenantId);
    }

    private long insertAttachmentFile(JdbcTemplate jdbc, long tenantId) {
        jdbc.update("INSERT INTO crm_attachment_file (tenant_id, uploader_actor_id, display_name, declared_media_type,"
                        + " expected_bytes, byte_state, inspection_state, publication_state)"
                        + " VALUES (?, ?, 'target.pdf', 'application/pdf', 4, 'UPLOADING', 'NOT_RUN', 'PRIVATE')",
                tenantId, tenantId);
        return jdbc.queryForObject("SELECT MAX(id) FROM crm_attachment_file WHERE tenant_id = ?", Long.class,
                tenantId);
    }

    private CrmParents insertCrmParents(JdbcTemplate jdbc, long tenantId) {
        long customerId = insertCustomer(jdbc, tenantId);
        long contactId = insertContact(jdbc, tenantId);
        jdbc.update("INSERT INTO crm_lead (tenant_id, owner_actor_id, name) VALUES (?, ?, ?)",
                tenantId, tenantId, "B24b typed attachment lead");
        long leadId = id(jdbc, "crm_lead", tenantId);
        OpportunityPipelineFixture.provisionActivePair(jdbc, tenantId, "DEFAULT", "NEW");
        jdbc.update("INSERT INTO crm_opportunity (tenant_id, owner_actor_id, customer_id, name, pipeline_code, stage_code)"
                        + " VALUES (?, ?, ?, ?, 'DEFAULT', 'NEW')",
                tenantId, tenantId, customerId, "B24b typed attachment opportunity");
        long opportunityId = id(jdbc, "crm_opportunity", tenantId);
        jdbc.update("INSERT INTO crm_contract (tenant_id, owner_actor_id, customer_id, name, contract_number,"
                        + " contract_number_key) VALUES (?, ?, ?, ?, ?, ?)",
                tenantId, tenantId, customerId, "B24b typed attachment contract", "B24B-CONTRACT",
                "B24B-CONTRACT");
        long contractId = id(jdbc, "crm_contract", tenantId);
        jdbc.update("INSERT INTO crm_invoice (tenant_id, owner_actor_id, customer_id, contract_id, application_number,"
                        + " amount, invoice_type, title_type, title) VALUES (?, ?, ?, ?, ?, 1.00, 'NORMAL',"
                        + " 'INDIVIDUAL', ?)",
                tenantId, tenantId, customerId, contractId, "B24B-INVOICE", "B24b individual");
        long invoiceId = id(jdbc, "crm_invoice", tenantId);
        jdbc.update("INSERT INTO crm_receivable_plan (tenant_id, owner_actor_id, customer_id, contract_id, installment,"
                        + " due_date, planned_amount, payment_method) VALUES (?, ?, ?, ?, 1, '2030-01-01', 1.00, 'BANK')",
                tenantId, tenantId, customerId, contractId);
        long planId = id(jdbc, "crm_receivable_plan", tenantId);
        jdbc.update("INSERT INTO crm_receivable (tenant_id, owner_actor_id, customer_id, contract_id, receivable_plan_id,"
                        + " receipt_number, received_date, payment_method, amount)"
                        + " VALUES (?, ?, ?, ?, ?, 'B24B-RECEIVABLE', '2030-01-01', 'BANK', 1.00)",
                tenantId, tenantId, customerId, contractId, planId);
        long receivableId = id(jdbc, "crm_receivable", tenantId);
        return new CrmParents(customerId, contactId, leadId, opportunityId, contractId, invoiceId,
                receivableId, planId);
    }

    private static String targetColumn(AttachmentTargetType type) {
        return switch (type) {
            case CUSTOMER -> "customer_id";
            case CONTACT -> "contact_id";
            case LEAD -> "lead_id";
            case OPPORTUNITY -> "opportunity_id";
            case CONTRACT -> "contract_id";
            case INVOICE -> "invoice_id";
            case RECEIVABLE -> "receivable_id";
            case RECEIVABLE_PLAN -> "receivable_plan_id";
            case PRODUCT -> "product_id";
            case RETURN_VISIT -> "return_visit_id";
            case MARKETING_ACTIVITY -> "marketing_activity_id";
            case WORK_TASK, OA_ANNOUNCEMENT, OA_REQUEST, IMPORT_DRAFT ->
                    throw new IllegalArgumentException("CRM target type is required");
        };
    }

    private static Stream<AttachmentTargetType> crmTargetTypes() {
        return Stream.of(AttachmentTargetType.CUSTOMER, AttachmentTargetType.CONTACT, AttachmentTargetType.LEAD,
                AttachmentTargetType.OPPORTUNITY, AttachmentTargetType.CONTRACT, AttachmentTargetType.INVOICE,
                AttachmentTargetType.RECEIVABLE, AttachmentTargetType.RECEIVABLE_PLAN);
    }

    private static long id(JdbcTemplate jdbc, String table, long tenantId) {
        return jdbc.queryForObject("SELECT MAX(id) FROM " + table + " WHERE tenant_id = ?", Long.class, tenantId);
    }

    @Override
    protected void clearTestData() {
        clearTenant(TENANT);
        clearTenant(FOREIGN_TENANT);
    }

    private void clearTenant(long tenantId) {
        executeSql("DELETE FROM crm_attachment_relation WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM crm_attachment_content WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM crm_attachment_read_lease WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM crm_attachment_job WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM crm_attachment_file WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM crm_work_task WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM crm_receivable WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM crm_receivable_plan WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM crm_invoice WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM crm_contract WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM crm_opportunity WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM crm_lead WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM crm_contact WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM crm_customer WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM organization_membership WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM organization_tenant WHERE tenant_id = " + tenantId);
        executeSql("DELETE FROM organization_member_profile WHERE actor_id = " + tenantId);
    }

    private record Fixture(long tenantId, long workTaskId, long fileId) { }

    private record CrmParents(long customerId, long contactId, long leadId, long opportunityId,
                              long contractId, long invoiceId, long receivableId, long receivablePlanId) {
        long id(AttachmentTargetType type) {
            return switch (type) {
                case CUSTOMER -> customerId;
                case CONTACT -> contactId;
                case LEAD -> leadId;
                case OPPORTUNITY -> opportunityId;
                case CONTRACT -> contractId;
                case INVOICE -> invoiceId;
                case RECEIVABLE -> receivableId;
                case RECEIVABLE_PLAN -> receivablePlanId;
                case PRODUCT, RETURN_VISIT, MARKETING_ACTIVITY, WORK_TASK, OA_ANNOUNCEMENT, OA_REQUEST, IMPORT_DRAFT ->
                        throw new IllegalArgumentException("CRM target type is required");
            };
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AttachmentPolicyConfiguration {
        @Bean
        @Primary
        AttachmentRuntimePolicy b24bAttachmentRuntimePolicy() {
            Duration second = Duration.ofSeconds(1);
            return new AttachmentRuntimePolicy(true, 16, 64, 64, 2, 2, 2, 2, 128, 1,
                    second, second, second, second, second, second, second, second,
                    Duration.ofSeconds(5), Duration.ofMillis(20), 3, second, Duration.ofSeconds(600), 1);
        }
    }
}
