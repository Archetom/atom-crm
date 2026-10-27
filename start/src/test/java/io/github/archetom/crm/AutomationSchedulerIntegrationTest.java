package io.github.archetom.crm;

import io.github.archetom.crm.application.automation.AutomationExecutionContext;
import io.github.archetom.crm.application.automation.AutomationKind;
import io.github.archetom.crm.application.automation.AutomationLeaseLostException;
import io.github.archetom.crm.application.automation.AutomationOutcome;
import io.github.archetom.crm.application.automation.AutomationPolicy;
import io.github.archetom.crm.application.port.out.automation.AutomationScheduleStore;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MySQL oracle for a B26 occurrence, fence, and same-transaction terminal effect. */
@RequiresDatabase
class AutomationSchedulerIntegrationTest extends BaseIntegrationTest {
    private static final long TENANT = 9_260_001L;

    @Autowired private AutomationScheduleStore schedules;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void v65OwnsOneClaimedOccurrenceAndFencesItsTerminalCommit() {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        resetAutomation(jdbc);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                TENANT, "B26 scheduler owner");
        jdbc.update("INSERT INTO organization_tenant (tenant_id, initialized_by_actor_id) VALUES (?, ?)",
                TENANT, TENANT);
        AutomationPolicy policy = policy();
        TenantId tenantId = new TenantId(TENANT);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            schedules.preparePoolConfiguration(tenantId, AutomationKind.CUSTOMER_POOL_RELEASE, true, policy);
            schedules.synchronize(tenantId, AutomationKind.CUSTOMER_POOL_RELEASE, true, policy);
        });

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_automation_schedule"
                + " WHERE tenant_id = ? AND kind = 'CUSTOMER_POOL_RELEASE' AND status = 'ACTIVE'", Integer.class,
                TENANT));
        assertEquals(1L, jdbc.queryForObject("SELECT generation FROM crm_automation_schedule"
                + " WHERE tenant_id = ? AND kind = 'CUSTOMER_POOL_RELEASE'", Long.class, TENANT));
        assertTrue(schedules.materializeOne(policy).isPresent());
        var claimed = schedules.claimOne(policy, UUID.randomUUID()).context();
        assertTrue(claimed.isPresent());
        AutomationExecutionContext context = claimed.orElseThrow();
        tx.executeWithoutResult(status -> {
            schedules.requireCurrent(context);
            schedules.succeed(context, AutomationOutcome.released(0));
        });

        int succeeded = jdbc.queryForObject("SELECT COUNT(*) FROM crm_automation_run"
                + " WHERE tenant_id = ? AND kind = 'CUSTOMER_POOL_RELEASE' AND status = 'SUCCEEDED'"
                + " AND affected_count = 0", Integer.class, TENANT);
        assertEquals(1, succeeded, () -> jdbc.queryForList("SELECT id, tenant_id, kind, generation, status, "
                + "attempt, lease_fence, affected_count FROM crm_automation_run WHERE tenant_id = ?", TENANT)
                .toString());
        assertTrue(schedules.claimOne(policy, UUID.randomUUID()).context().isEmpty());

        tx.executeWithoutResult(status -> schedules.synchronize(tenantId,
                AutomationKind.CUSTOMER_POOL_RELEASE, false, policy));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_automation_schedule"
                + " WHERE tenant_id = ? AND kind = 'CUSTOMER_POOL_RELEASE' AND status = 'PAUSED'"
                + " AND generation = 2 AND next_due_at IS NULL", Integer.class, TENANT));

        tx.executeWithoutResult(status -> schedules.synchronize(tenantId,
                AutomationKind.CUSTOMER_POOL_RELEASE, true, policy));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_automation_schedule"
                + " WHERE tenant_id = ? AND kind = 'CUSTOMER_POOL_RELEASE' AND status = 'ACTIVE'"
                + " AND generation = 3 AND next_due_at IS NOT NULL", Integer.class, TENANT));
    }

    @Test
    void effectCanPauseItsScheduleWithoutCancellingItsOwnLeasedRun() {
        long tenant = TENANT + 50;
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        resetAutomation(jdbc);
        bootstrapTenant(jdbc, tenant, "B27 current run owner");
        AutomationPolicy policy = policy();
        TenantId tenantId = new TenantId(tenant);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        activate(tx, tenantId, AutomationKind.WORK_TASK_RECURRENCE, policy);
        assertTrue(schedules.materializeOne(policy).isPresent());
        AutomationExecutionContext context = schedules.claimOne(policy, UUID.randomUUID()).context().orElseThrow();

        tx.executeWithoutResult(status -> {
            schedules.requireCurrent(context);
            schedules.synchronizeCurrentRun(context, false, policy);
            schedules.succeed(context, AutomationOutcome.released(0));
        });

        assertEquals("PAUSED", jdbc.queryForObject("SELECT status FROM crm_automation_schedule "
                + "WHERE tenant_id = ? AND kind = 'WORK_TASK_RECURRENCE'", String.class, tenant));
        assertEquals("SUCCEEDED", jdbc.queryForObject("SELECT status FROM crm_automation_run WHERE id = ?",
                String.class, context.runId()));
    }

    @Test
    void exhaustedAndExpiredRunsBecomeDeadBeforeAnotherClaim() {
        long tenant = TENANT + 1;
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        resetAutomation(jdbc);
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                tenant, "B26 exhausted run owner");
        jdbc.update("INSERT INTO organization_tenant (tenant_id, initialized_by_actor_id) VALUES (?, ?)",
                tenant, tenant);
        AutomationPolicy policy = policy();
        TenantId tenantId = new TenantId(tenant);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            schedules.preparePoolConfiguration(tenantId, AutomationKind.CUSTOMER_POOL_RELEASE, true, policy);
            schedules.synchronize(tenantId, AutomationKind.CUSTOMER_POOL_RELEASE, true, policy);
        });
        assertTrue(schedules.materializeOne(policy).isPresent());
        jdbc.update("UPDATE crm_automation_run SET attempt = ?, status = 'READY', available_at = UTC_TIMESTAMP(6) "
                        + "WHERE tenant_id = ? AND kind = 'CUSTOMER_POOL_RELEASE'",
                policy.maxAttempts(), tenant);

        assertTrue(schedules.claimOne(policy, UUID.randomUUID()).context().isEmpty());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_automation_run "
                        + "WHERE tenant_id = ? AND status = 'DEAD' AND safe_reason = 'RETRY_EXHAUSTED'",
                Integer.class, tenant));

        tx.executeWithoutResult(status -> {
            schedules.preparePoolConfiguration(tenantId, AutomationKind.CONTACT_POOL_RELEASE, true, policy);
            schedules.synchronize(tenantId, AutomationKind.CONTACT_POOL_RELEASE, true, policy);
        });
        assertTrue(schedules.materializeOne(policy).isPresent());
        jdbc.update("UPDATE crm_automation_run SET status = 'RETRY_WAIT', available_at = UTC_TIMESTAMP(6) "
                        + "+ INTERVAL 1 HOUR, deadline_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND "
                        + "WHERE tenant_id = ? AND kind = 'CONTACT_POOL_RELEASE'",
                tenant);

        assertTrue(schedules.claimOne(policy, UUID.randomUUID()).context().isEmpty());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_automation_run "
                        + "WHERE tenant_id = ? AND kind = 'CONTACT_POOL_RELEASE' AND status = 'DEAD'",
                Integer.class, tenant));
    }

    @Test
    void expiredOwnerCannotWriteRecoveryAndActiveRunDoesNotStarveAnotherSchedule() {
        long firstTenant = TENANT + 2;
        long secondTenant = TENANT + 3;
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        resetAutomation(jdbc);
        bootstrapTenant(jdbc, firstTenant, "B26 lease owner");
        bootstrapTenant(jdbc, secondTenant, "B26 unblocked owner");
        AutomationPolicy policy = policy();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        activate(tx, new TenantId(firstTenant), AutomationKind.CUSTOMER_POOL_RELEASE, policy);
        activate(tx, new TenantId(secondTenant), AutomationKind.CUSTOMER_POOL_RELEASE, policy);
        assertTrue(schedules.materializeOne(policy).isPresent());
        AutomationExecutionContext claimed = schedules.claimOne(policy, UUID.randomUUID()).context().orElseThrow();
        assertEquals(firstTenant, claimed.tenantId().getValue());
        jdbc.update("UPDATE crm_automation_run SET lease_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND "
                        + "WHERE id = ?", claimed.runId());

        assertThrows(AutomationLeaseLostException.class, () -> schedules.recover(claimed, policy));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_automation_run "
                        + "WHERE id = ? AND status = 'LEASED'", Integer.class, claimed.runId()));

        jdbc.update("UPDATE crm_automation_schedule SET next_due_at = UTC_TIMESTAMP(6) "
                        + "WHERE tenant_id = ? AND kind = 'CUSTOMER_POOL_RELEASE'", firstTenant);
        assertTrue(schedules.materializeOne(policy).isPresent());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_automation_run "
                        + "WHERE tenant_id = ? AND kind = 'CUSTOMER_POOL_RELEASE'", Integer.class, secondTenant));
    }

    @Test
    void newerCadenceRevisionReconcilesSchedulesAndRejectsTheOlderReplica() {
        long tenant = TENANT + 4;
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        resetAutomation(jdbc);
        bootstrapTenant(jdbc, tenant, "B26 revision owner");
        AutomationPolicy v1 = policy();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        activate(tx, new TenantId(tenant), AutomationKind.CUSTOMER_POOL_RELEASE, v1);
        assertTrue(schedules.materializeOne(v1).isPresent());
        assertTrue(schedules.claimOne(v1, UUID.randomUUID()).context().isPresent());

        AutomationPolicy v2 = new AutomationPolicy(true, true, "B26A_V2", Duration.ofHours(1), 200,
                Duration.ofMinutes(2), Duration.ofHours(24), 5, Duration.ofMinutes(1), Duration.ofDays(14));
        assertEquals("READY", schedules.reconcileCadenceRevision(v2).readiness().name());
        assertEquals("REVISION_DRIFT", schedules.schedulingReadiness(v1).name());
        assertEquals("READY", schedules.schedulingReadiness(v2).name());
        assertTrue(schedules.claimOne(v1, UUID.randomUUID()).context().isEmpty());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_automation_schedule "
                        + "WHERE tenant_id = ? AND kind = 'CUSTOMER_POOL_RELEASE' "
                        + "AND cadence_revision = 'B26A_V2' AND generation = 2", Integer.class, tenant));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_automation_run "
                        + "WHERE tenant_id = ? AND status = 'CANCELLED' AND safe_reason = 'SCHEDULE_CHANGED'",
                Integer.class, tenant));
        assertTrue(schedules.materializeOne(v2).isPresent());
    }

    private static void bootstrapTenant(org.springframework.jdbc.core.JdbcTemplate jdbc, long tenant, String name) {
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)", tenant, name);
        jdbc.update("INSERT INTO organization_tenant (tenant_id, initialized_by_actor_id) VALUES (?, ?)", tenant, tenant);
    }

    private static void resetAutomation(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM crm_automation_schedule");
        jdbc.update("UPDATE crm_automation_control SET cadence_revision = 'B26A_V1', reconciled_time = UTC_TIMESTAMP(6) "
                + "WHERE control_key = 'POOL_CADENCE'");
    }

    private void activate(TransactionTemplate tx, TenantId tenantId, AutomationKind kind, AutomationPolicy policy) {
        tx.executeWithoutResult(status -> {
            schedules.preparePoolConfiguration(tenantId, kind, true, policy);
            schedules.synchronize(tenantId, kind, true, policy);
        });
    }

    private static AutomationPolicy policy() {
        return new AutomationPolicy(true, true, "B26A_V1", Duration.ofHours(1), 200,
                Duration.ofMinutes(2), Duration.ofHours(24), 5, Duration.ofMinutes(1), Duration.ofDays(14));
    }
}
