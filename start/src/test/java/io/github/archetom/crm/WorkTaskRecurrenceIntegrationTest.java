package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesCreateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesVersionRequest;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesActionResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesDetailResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesMutationResultResponse;
import io.github.archetom.crm.application.automation.AutomationExecutionContext;
import io.github.archetom.crm.application.automation.AutomationKind;
import io.github.archetom.crm.application.automation.AutomationOutcome;
import io.github.archetom.crm.application.automation.AutomationPolicy;
import io.github.archetom.crm.application.automation.WorkRecurrencePolicy;
import io.github.archetom.crm.application.automation.WorkRecurrenceReadiness;
import io.github.archetom.crm.application.automation.WorkTaskRecurrenceAutomationHandler;
import io.github.archetom.crm.application.port.out.automation.AutomationScheduleStore;
import io.github.archetom.crm.application.port.out.automation.WorkRecurrenceControlStore;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.properties.WorkRecurrenceProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MySQL B27a vertical slice: API series, fenced effect, crash rollback, and open-slot coalescing. */
@RequiresDatabase
class WorkTaskRecurrenceIntegrationTest extends BaseIntegrationTest {
    private static final long TENANT = 9_270_001L;
    private static final Set<AutomationKind> RECURRENCE_ONLY = Set.of(AutomationKind.WORK_TASK_RECURRENCE);

    @Autowired private AutomationScheduleStore schedules;
    @Autowired private WorkRecurrenceControlStore recurrenceControl;
    @Autowired private WorkTaskRecurrenceAutomationHandler recurrenceHandler;
    @Autowired private SchedulingProperties schedulingProperties;
    @Autowired private WorkRecurrenceProperties recurrenceProperties;
    @Autowired private PlatformTransactionManager transactionManager;

    private boolean originalSchedulingEnabled;

    @BeforeEach
    void prepareRecurrenceLane() {
        originalSchedulingEnabled = schedulingProperties.isEnabled();
        schedulingProperties.setEnabled(true);
        clearRecurrenceRows();
        AutomationPolicy policy = schedulingProperties.policy();
        assertEquals("READY", schedules.reconcileCadenceRevision(policy).readiness().name());
        assertEquals("READY", recurrenceControl.reconcile(recurrenceProperties.policy()).name());
    }

    @AfterEach
    void restoreRecurrenceLane() {
        schedulingProperties.setEnabled(originalSchedulingEnabled);
        clearRecurrenceRows();
    }

    @Test
    void apiSeriesMaterializesAtomicallyAndCoalescesTheSecondOpenOccurrence() throws Exception {
        bootstrapOwner();
        WorkTaskSeriesMutationResultResponse created = createDraft();
        WorkTaskSeriesDetailResponse active = activate(created);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        LocalDateTime forcedCursor = active.nextNominalLocalTime().minusDays(1);
        forceSeriesCursor(jdbc, active.id(), forcedCursor);

        // The API projection must return the durable cursor, not a locally re-resolved tzdb value.
        assertEquals(forcedCursor, getSeries(active.id()).nextDueTime());

        AutomationPolicy policy = schedulingProperties.policy();
        assertTrue(schedules.materializeOne(policy, RECURRENCE_ONLY).isPresent());
        AutomationExecutionContext firstOwner = schedules.claimOne(policy, UUID.randomUUID(), RECURRENCE_ONLY)
                .context().orElseThrow();
        assertTrue(schedules.claimOne(policy, UUID.randomUUID(), RECURRENCE_ONLY).context().isEmpty());

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            recurrenceHandler.execute(firstOwner);
            throw new IllegalStateException("simulated crash before run success");
        }));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM crm_work_task WHERE tenant_id = ?", TENANT));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM crm_work_task_series_occurrence WHERE tenant_id = ?", TENANT));
        assertEquals("LEASED", jdbc.queryForObject("SELECT status FROM crm_automation_run WHERE id = ?",
                String.class, firstOwner.runId()));

        schedules.recover(firstOwner, policy);
        jdbc.update("UPDATE crm_automation_run SET available_at = UTC_TIMESTAMP(6) WHERE id = ?", firstOwner.runId());
        AutomationExecutionContext retryOwner = schedules.claimOne(policy, UUID.randomUUID(), RECURRENCE_ONLY)
                .context().orElseThrow();
        assertThrows(RuntimeException.class, () -> transaction.executeWithoutResult(status ->
                recurrenceHandler.execute(firstOwner)));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM crm_work_task WHERE tenant_id = ?", TENANT));
        transaction.executeWithoutResult(status -> {
            assertEquals(AutomationOutcome.released(1), recurrenceHandler.execute(retryOwner));
            schedules.succeed(retryOwner, AutomationOutcome.released(1));
        });

        assertEquals("SUCCEEDED", jdbc.queryForObject("SELECT status FROM crm_automation_run WHERE id = ?",
                String.class, retryOwner.runId()));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM crm_work_task WHERE tenant_id = ? AND origin_kind = 'RECURRENCE'", TENANT));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM crm_work_task_series_occurrence "
                + "WHERE tenant_id = ? AND status = 'MATERIALIZED'", TENANT));
        LocalDateTime taskCreatedTime = jdbc.queryForObject("SELECT created_time FROM crm_work_task "
                + "WHERE tenant_id = ? AND origin_kind = 'RECURRENCE'", LocalDateTime.class, TENANT);
        assertEquals(taskCreatedTime, jdbc.queryForObject("SELECT created_time "
                + "FROM crm_work_task_series_occurrence WHERE tenant_id = ? AND status = 'MATERIALIZED'",
                LocalDateTime.class, TENANT));
        assertEquals(taskCreatedTime, jdbc.queryForObject("SELECT updated_time FROM crm_work_task_series "
                + "WHERE tenant_id = ? AND id = ?", LocalDateTime.class, TENANT, active.id()));
        LocalDateTime completedTime = jdbc.queryForObject("SELECT completed_time FROM crm_automation_run WHERE id = ?",
                LocalDateTime.class, retryOwner.runId());
        assertTrue(!completedTime.isBefore(taskCreatedTime));

        forceSeriesCursor(jdbc, active.id(), forcedCursor);
        jdbc.update("UPDATE crm_automation_schedule SET next_due_at = UTC_TIMESTAMP(6) "
                + "WHERE tenant_id = ? AND kind = 'WORK_TASK_RECURRENCE'", TENANT);
        assertTrue(schedules.materializeOne(policy, RECURRENCE_ONLY).isPresent());
        AutomationExecutionContext openTaskRun = schedules.claimOne(policy, UUID.randomUUID(), RECURRENCE_ONLY)
                .context().orElseThrow();
        transaction.executeWithoutResult(status -> {
            assertEquals(AutomationOutcome.released(0), recurrenceHandler.execute(openTaskRun));
            schedules.succeed(openTaskRun, AutomationOutcome.released(0));
        });

        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM crm_work_task WHERE tenant_id = ? AND origin_kind = 'RECURRENCE'", TENANT));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM crm_work_task_series_occurrence "
                + "WHERE tenant_id = ? AND status = 'SKIPPED' AND safe_reason = 'OPEN_TASK'", TENANT));
        assertEquals("SUCCEEDED", jdbc.queryForObject("SELECT status FROM crm_automation_run WHERE id = ?",
                String.class, openTaskRun.runId()));
    }

    @Test
    void apiRejectsAClientSuppliedFixedOffsetInsteadOfAnIanaZone() throws Exception {
        bootstrapOwner();
        LocalDateTime now = LocalDateTime.now(java.time.Clock.systemUTC()).withSecond(0).withNano(0);
        WorkTaskSeriesCreateRequest request = new WorkTaskSeriesCreateRequest().setTitle("Invalid zone")
                .setAssigneeActorId(TENANT).setPriority("NORMAL")
                .setRecurrenceFrequency("DAILY").setRecurrenceInterval(1).setZoneId("+08:00")
                .setStartLocalDate(now.toLocalDate()).setLocalTime(now.toLocalTime());

        performAs(TENANT, post("/api/v1/work/task-series").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request))).andExpect(status().isBadRequest());
    }

    @Test
    void apiAcceptsExplicitEmptyWeekdaysForDailyRule() throws Exception {
        bootstrapOwner();
        LocalDateTime now = LocalDateTime.now(java.time.Clock.systemUTC()).withSecond(0).withNano(0);
        WorkTaskSeriesMutationResultResponse created = mutationResponse(performAs(TENANT,
                post("/api/v1/work/task-series").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskSeriesCreateRequest().setTitle("Monthly renewal")
                                .setAssigneeActorId(TENANT).setPriority("NORMAL")
                                .setRecurrenceFrequency("DAILY").setRecurrenceInterval(1).setZoneId("UTC")
                                .setStartLocalDate(now.toLocalDate().minusDays(1))
                                .setLocalTime(now.toLocalTime())
                                .setWeekdays(List.of())))));

        assertEquals(List.of(), getSeries(created.id()).recurrence().weekdays());
    }

    @Test
    void staleEligibleKindsCannotMaterializeOrClaimAfterRecurrenceStartsReconciling() throws Exception {
        bootstrapOwner();
        WorkTaskSeriesDetailResponse active = activate(createDraft());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        forceSeriesCursor(jdbc, active.id(), active.nextNominalLocalTime().minusDays(1));
        jdbc.update("UPDATE crm_automation_schedule SET next_due_at = UTC_TIMESTAMP(6) "
                + "WHERE tenant_id = ? AND kind = 'WORK_TASK_RECURRENCE'", TENANT);
        jdbc.update("UPDATE crm_work_recurrence_control SET reconciliation_status = 'RECONCILING', "
                + "target_tzdb_fingerprint = tzdb_fingerprint, target_policy_revision = policy_revision "
                + "WHERE control_key = 'RECURRENCE_TZDB'");

        try {
            AutomationPolicy policy = schedulingProperties.policy();
            assertTrue(schedules.materializeOne(policy, RECURRENCE_ONLY).isEmpty());
            assertTrue(schedules.claimOne(policy, UUID.randomUUID(), RECURRENCE_ONLY).context().isEmpty());
            // Scoped to the lane the two calls above can write. The pool lanes share this table
            // and this class only clears the recurrence kind, so an unscoped count also fails on a
            // concurrent pool run that neither call produced.
            assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM crm_automation_run "
                    + "WHERE tenant_id = ? AND kind = 'WORK_TASK_RECURRENCE'", TENANT));
        } finally {
            jdbc.update("UPDATE crm_work_recurrence_control SET reconciliation_status = 'READY', "
                    + "target_tzdb_fingerprint = NULL, target_policy_revision = NULL, "
                    + "cursor_tenant_id = NULL, cursor_series_id = NULL WHERE control_key = 'RECURRENCE_TZDB'");
        }
    }

    @Test
    void concurrentRecurrenceAdmissionAndReconciliationUseOneControlLockOrder() throws Exception {
        bootstrapOwner();
        WorkTaskSeriesDetailResponse active = activate(createDraft());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        forceSeriesCursor(jdbc, active.id(), active.nextNominalLocalTime().minusDays(1));
        WorkRecurrencePolicy current = recurrenceProperties.policy();
        WorkRecurrencePolicy newer = new WorkRecurrencePolicy(true, true, "B27A_V2", current.tzdbFingerprint());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> admission = executor.submit(() -> {
                ready.countDown();
                await(start);
                schedules.materializeOne(schedulingProperties.policy(), RECURRENCE_ONLY);
            });
            Future<WorkRecurrenceReadiness> reconciliation = executor.submit(() -> {
                ready.countDown();
                await(start);
                return recurrenceControl.reconcile(newer);
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            admission.get(10, TimeUnit.SECONDS);
            assertEquals(WorkRecurrenceReadiness.READY, reconciliation.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            jdbc.update("UPDATE crm_work_recurrence_control SET tzdb_fingerprint = ?, policy_revision = ?, "
                            + "reconciliation_status = 'READY', target_tzdb_fingerprint = NULL, "
                            + "target_policy_revision = NULL, cursor_tenant_id = NULL, cursor_series_id = NULL "
                            + "WHERE control_key = 'RECURRENCE_TZDB'",
                    current.tzdbFingerprint(), current.policyRevision());
        }
    }

    @Test
    void revokedCreatorPausesTheSeriesAndSettlesTheCurrentRun() throws Exception {
        bootstrapOwner();
        WorkTaskSeriesDetailResponse active = activate(createDraft());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        forceSeriesCursor(jdbc, active.id(), active.nextNominalLocalTime().minusDays(1));
        AutomationPolicy policy = schedulingProperties.policy();
        assertTrue(schedules.materializeOne(policy, RECURRENCE_ONLY).isPresent());
        AutomationExecutionContext context = schedules.claimOne(policy, UUID.randomUUID(), RECURRENCE_ONLY)
                .context().orElseThrow();
        jdbc.update("UPDATE organization_membership SET status = 'SUSPENDED' WHERE tenant_id = ? AND actor_id = ?",
                TENANT, TENANT);

        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.executeWithoutResult(status -> {
                assertEquals(AutomationOutcome.released(0), recurrenceHandler.execute(context));
                schedules.succeed(context, AutomationOutcome.released(0));
            });
            assertEquals("PAUSED", jdbc.queryForObject("SELECT status FROM crm_work_task_series WHERE tenant_id = ? AND id = ?",
                    String.class, TENANT, active.id()));
            assertEquals("CREATOR_ACCESS_REVOKED", jdbc.queryForObject("SELECT last_safe_reason "
                    + "FROM crm_work_task_series WHERE tenant_id = ? AND id = ?", String.class, TENANT, active.id()));
            assertEquals("PAUSED", jdbc.queryForObject("SELECT status FROM crm_automation_schedule "
                    + "WHERE tenant_id = ? AND kind = 'WORK_TASK_RECURRENCE'", String.class, TENANT));
            assertEquals("SUCCEEDED", jdbc.queryForObject("SELECT status FROM crm_automation_run WHERE id = ?",
                    String.class, context.runId()));
            assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM crm_work_task WHERE tenant_id = ?", TENANT));
        } finally {
            jdbc.update("UPDATE organization_membership SET status = 'ACTIVE' WHERE tenant_id = ? AND actor_id = ?",
                    TENANT, TENANT);
        }
    }

    @Test
    void revokedFollowerIsOmittedWithoutBlockingMaterialization() throws Exception {
        bootstrapOwner();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long follower = TENANT + 101;
        provisionOwner(jdbc, follower);
        WorkTaskSeriesDetailResponse active = activate(createDraft(List.of(follower)));
        jdbc.update("DELETE FROM authorization_role_assignment WHERE tenant_id = ? AND actor_id = ?", TENANT, follower);
        forceSeriesCursor(jdbc, active.id(), active.nextNominalLocalTime().minusDays(1));

        AutomationPolicy policy = schedulingProperties.policy();
        assertTrue(schedules.materializeOne(policy, RECURRENCE_ONLY).isPresent());
        AutomationExecutionContext context = schedules.claimOne(policy, UUID.randomUUID(), RECURRENCE_ONLY)
                .context().orElseThrow();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            assertEquals(AutomationOutcome.released(1), recurrenceHandler.execute(context));
            schedules.succeed(context, AutomationOutcome.released(1));
        });

        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM crm_work_task WHERE tenant_id = ? AND origin_kind = 'RECURRENCE'", TENANT));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM crm_work_task_follower WHERE tenant_id = ?", TENANT));
    }

    @Test
    void apiRejectsAssigneeOutsideTheTenantAndHidesSeriesFromAnUnrelatedReader() throws Exception {
        bootstrapOwner();
        WorkTaskSeriesMutationResultResponse created = createDraft();
        LocalDateTime now = LocalDateTime.now(java.time.Clock.systemUTC()).withSecond(0).withNano(0);
        WorkTaskSeriesCreateRequest invalidAssignee = new WorkTaskSeriesCreateRequest().setTitle("Invalid assignee")
                .setAssigneeActorId(TENANT + 999).setPriority("NORMAL")
                .setRecurrenceFrequency("DAILY").setRecurrenceInterval(1).setZoneId("UTC")
                .setStartLocalDate(now.toLocalDate()).setLocalTime(now.toLocalTime());
        performAs(TENANT, post("/api/v1/work/task-series").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(invalidAssignee))).andExpect(status().isBadRequest());

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long reader = TENANT + 102;
        provisionReadOnlyMember(jdbc, reader);
        performAs(reader, get("/api/v1/work/task-series/{id}", created.id())).andExpect(status().isNotFound());
    }

    private void bootstrapOwner() throws Exception {
        if (count(new JdbcTemplate(dataSource), "SELECT COUNT(*) FROM organization_tenant WHERE tenant_id = ?", TENANT) > 0) {
            return;
        }
        performAs(TENANT, post("/api/v1/organization/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName("B27 recurrence owner"))))
                .andExpect(status().isOk());
    }

    @Test
    void apiSeriesProjectionReturnsServerComputedActionsAndVersionedDetailBaseline() throws Exception {
        bootstrapOwner();
        WorkTaskSeriesMutationResultResponse created = createDraft();

        assertTrue(created.allowedActions().contains(WorkTaskSeriesActionResponse.UPDATE));
        assertTrue(created.allowedActions().contains(WorkTaskSeriesActionResponse.ACTIVATE));
        assertTrue(created.allowedActions().contains(WorkTaskSeriesActionResponse.STOP));
        WorkTaskSeriesDetailResponse active = activate(created);
        assertEquals("ACTIVE", active.status().name());
        assertTrue(active.allowedActions().contains(WorkTaskSeriesActionResponse.UPDATE));
        assertTrue(active.allowedActions().contains(WorkTaskSeriesActionResponse.PAUSE));
        assertTrue(active.allowedActions().contains(WorkTaskSeriesActionResponse.STOP));
    }

    private WorkTaskSeriesMutationResultResponse createDraft() throws Exception {
        return createDraft(List.of());
    }

    private WorkTaskSeriesMutationResultResponse createDraft(List<Long> followerActorIds) throws Exception {
        LocalDateTime now = LocalDateTime.now(java.time.Clock.systemUTC()).withSecond(0).withNano(0);
        WorkTaskSeriesCreateRequest request = new WorkTaskSeriesCreateRequest().setTitle("Monthly renewal")
                .setAssigneeActorId(TENANT).setPriority("NORMAL")
                .setRecurrenceFrequency("DAILY").setRecurrenceInterval(1).setZoneId("UTC")
                .setStartLocalDate(now.toLocalDate().minusDays(1)).setLocalTime(now.toLocalTime())
                .setFollowerActorIds(followerActorIds);
        return mutationResponse(performAs(TENANT, post("/api/v1/work/task-series")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request))));
    }

    private WorkTaskSeriesDetailResponse activate(WorkTaskSeriesMutationResultResponse series) throws Exception {
        mutationResponse(performAs(TENANT, post("/api/v1/work/task-series/{id}/activate", series.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new WorkTaskSeriesVersionRequest().setVersion(series.version())))));
        return getSeries(series.id());
    }

    private WorkTaskSeriesDetailResponse getSeries(Long seriesId) throws Exception {
        return fromJson(performAs(TENANT, get("/api/v1/work/task-series/{id}", seriesId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                WorkTaskSeriesDetailResponse.class);
    }

    private WorkTaskSeriesMutationResultResponse mutationResponse(ResultActions result) throws Exception {
        return fromJson(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                WorkTaskSeriesMutationResultResponse.class);
    }

    private ResultActions performAs(long actorId, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("X-Dev-User-Id", actorId).header("X-Dev-Tenant-Id", TENANT));
    }

    private static int count(JdbcTemplate jdbc, String sql, long tenantId) {
        return jdbc.queryForObject(sql, Integer.class, tenantId);
    }

    private static void forceSeriesCursor(JdbcTemplate jdbc, Long seriesId, LocalDateTime due) {
        jdbc.update("UPDATE crm_work_task_series SET next_nominal_local_time = ?, next_due_at = ? "
                + "WHERE tenant_id = ? AND id = ?", due, due, TENANT, seriesId);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("concurrent recurrence operation did not start");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("concurrent recurrence operation was interrupted", exception);
        }
    }

    private static void provisionOwner(JdbcTemplate jdbc, long actorId) {
        jdbc.update("INSERT IGNORE INTO organization_membership (tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')",
                TENANT, actorId);
        Long ownerRoleId = jdbc.queryForObject("SELECT id FROM authorization_role "
                + "WHERE tenant_id = ? AND system_key = 'TENANT_OWNER'", Long.class, TENANT);
        jdbc.update("INSERT IGNORE INTO authorization_role_assignment (tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT, actorId, ownerRoleId);
    }

    private static void provisionReadOnlyMember(JdbcTemplate jdbc, long actorId) {
        jdbc.update("INSERT IGNORE INTO organization_membership (tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')",
                TENANT, actorId);
        String roleName = "B27 recurrence read-only " + actorId;
        jdbc.update("INSERT IGNORE INTO authorization_role (tenant_id, name, description, status) VALUES (?, ?, ?, 'ACTIVE')",
                TENANT, roleName, "B27 recurrence visibility fixture");
        Long roleId = jdbc.queryForObject("SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT, roleName);
        jdbc.update("INSERT IGNORE INTO authorization_role_grant (tenant_id, role_id, authority_code) "
                + "VALUES (?, ?, 'work-tasks:read')", TENANT, roleId);
        jdbc.update("INSERT IGNORE INTO authorization_role_assignment (tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT, actorId, roleId);
    }

    private void clearRecurrenceRows() {
        if (dataSource == null) return;
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM crm_automation_run WHERE tenant_id = ? AND kind = 'WORK_TASK_RECURRENCE'", TENANT);
        jdbc.update("DELETE FROM crm_automation_schedule WHERE tenant_id = ? AND kind = 'WORK_TASK_RECURRENCE'", TENANT);
        jdbc.update("DELETE FROM crm_work_task_series_occurrence WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM crm_work_task_comment WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM crm_work_task_follower WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM crm_work_task WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM crm_work_task_series_follower WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM crm_work_task_series_weekday WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM crm_work_task_series WHERE tenant_id = ?", TENANT);
    }
}
