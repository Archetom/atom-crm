package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.NotificationPreferenceUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskCreateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskAssigneeUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskReminderPlanUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskReminderStepRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesCreateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskVersionRequest;
import io.github.archetom.crm.api.dto.response.WorkTaskReminderPlanResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesMutationResultResponse;
import io.github.archetom.crm.application.automation.NotificationReminderPump;
import io.github.archetom.crm.application.automation.NotificationDeliveryPump;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchGuarantee;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchOutcome;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryStore;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryStatus;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real V69/V70, transaction and lock evidence for the in-app Work reminder vertical slice. */
@RequiresDatabase
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "atom.scheduling.pulses-enabled=false")
class WorkTaskReminderIntegrationTest extends BaseIntegrationTest {
    private static final long OWNER = 810_001L;
    private static final long RECIPIENT = 810_002L;
    private static final long TENANT = 910_001L;

    @Autowired private NotificationReminderPump pump;
    @Autowired private NotificationDeliveryPump deliveryPump;
    @Autowired private NotificationDeliveryStore deliveryStore;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void createsOneNotificationAndKeepsTheFirstReadReceiptAcrossAStaleTaskProjection() throws Exception {
        bootstrap();
        WorkTaskResponse task = createDueTask("receipt evidence");
        replaceAtDueReminder(task);

        pump.runOnce();
        long notificationId = jdbc.queryForObject("SELECT id FROM crm_notification WHERE tenant_id = ? "
                + "AND work_task_id = ?", Long.class, TENANT, task.getId());
        assertEquals(1L, count("SELECT COUNT(*) FROM crm_notification WHERE tenant_id = ? AND work_task_id = ?",
                TENANT, task.getId()));
        assertEquals("EMITTED", occurrenceStatus(task.getId()));
        pump.runOnce();
        assertEquals(1L, count("SELECT COUNT(*) FROM crm_notification WHERE tenant_id = ? AND work_task_id = ?",
                TENANT, task.getId()));

        List<Integer> results = concurrently(
                () -> markRead(notificationId),
                () -> markRead(notificationId));
        assertEquals(List.of(200, 200), results.stream().sorted().toList());
        Timestamp firstRead = jdbc.queryForObject("SELECT read_time FROM crm_notification WHERE id = ?",
                Timestamp.class, notificationId);
        assertNotNull(firstRead);

        WorkTaskResponse current = task(task.getId());
        long projectionBefore = jdbc.queryForObject("SELECT notification_projection_version FROM crm_work_task "
                + "WHERE tenant_id = ? AND id = ?", Long.class, TENANT, task.getId());
        response(performAs(OWNER, TENANT, put("/api/v1/work/tasks/{id}", task.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new WorkTaskUpdateRequest().setTitle("receipt evidence changed")
                        .setDescription(current.getDescription()).setPriority(current.getPriority())
                        .setDueTime(current.getDueTime()).setVersion(current.getVersion()))))
                .andExpect(status().isOk()));
        assertTrue(jdbc.queryForObject("SELECT notification_projection_version FROM crm_work_task "
                        + "WHERE tenant_id = ? AND id = ?", Long.class, TENANT, task.getId()) > projectionBefore);
        assertEquals("READ", jdbc.queryForObject("SELECT status FROM crm_notification WHERE id = ?",
                String.class, notificationId));
        assertEquals(firstRead, jdbc.queryForObject("SELECT read_time FROM crm_notification WHERE id = ?",
                Timestamp.class, notificationId));
    }

    @Test
    void takesOverOneExpiredLeaseAndDeadLettersAnOccurrencePastItsHardDeadline() throws Exception {
        bootstrap();
        WorkTaskResponse leasedTask = createDueTask("expired lease");
        replaceAtDueReminder(leasedTask);
        jdbc.update("UPDATE crm_work_task_reminder_occurrence SET status = 'LEASED', attempt_count = 1, "
                        + "lease_owner = '11111111-1111-1111-1111-111111111111', lease_fence = 1, "
                        + "lease_until = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND "
                        + "WHERE tenant_id = ? AND work_task_id = ?",
                TENANT, leasedTask.getId());

        pump.runOnce();
        assertEquals("EMITTED", occurrenceStatus(leasedTask.getId()));
        assertEquals(1L, count("SELECT COUNT(*) FROM crm_notification WHERE tenant_id = ? AND work_task_id = ?",
                TENANT, leasedTask.getId()));
        assertTrue(jdbc.queryForObject("SELECT lease_fence FROM crm_work_task_reminder_occurrence "
                        + "WHERE tenant_id = ? AND work_task_id = ?", Long.class, TENANT, leasedTask.getId()) >= 2L);

        WorkTaskResponse expiredTask = createDueTask("hard deadline");
        replaceAtDueReminder(expiredTask);
        jdbc.update("UPDATE crm_work_task_reminder_occurrence SET deadline_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND "
                        + "WHERE tenant_id = ? AND work_task_id = ?",
                TENANT, expiredTask.getId());

        pump.runOnce();
        assertEquals("DEAD", occurrenceStatus(expiredTask.getId()));
        assertEquals(0L, count("SELECT COUNT(*) FROM crm_notification WHERE tenant_id = ? AND work_task_id = ?",
                TENANT, expiredTask.getId()));
    }

    @Test
    void deletesExpiredNotificationBeforeItsTerminalOccurrence() throws Exception {
        bootstrap();
        WorkTaskResponse task = createDueTask("retention order");
        replaceAtDueReminder(task);
        pump.runOnce();
        assertEquals(1L, count("SELECT COUNT(*) FROM crm_notification WHERE tenant_id = ? AND work_task_id = ?",
                TENANT, task.getId()));

        WorkTaskResponse current = task(task.getId());
        response(performAs(OWNER, TENANT, post("/api/v1/work/tasks/{id}/cancel", task.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new WorkTaskVersionRequest().setVersion(current.getVersion()))))
                .andExpect(status().isOk()));
        jdbc.update("UPDATE crm_notification SET created_time = UTC_TIMESTAMP(6) - INTERVAL 100 DAY "
                        + "WHERE tenant_id = ? AND work_task_id = ?", TENANT, task.getId());
        jdbc.update("UPDATE crm_work_task_reminder_occurrence SET terminal_time = UTC_TIMESTAMP(6) - INTERVAL 15 DAY "
                        + "WHERE tenant_id = ? AND work_task_id = ?", TENANT, task.getId());

        pump.runOnce();
        assertEquals(0L, count("SELECT COUNT(*) FROM crm_notification WHERE tenant_id = ? AND work_task_id = ?",
                TENANT, task.getId()));
        assertEquals(0L, count("SELECT COUNT(*) FROM crm_work_task_reminder_occurrence "
                        + "WHERE tenant_id = ? AND work_task_id = ?", TENANT, task.getId()));
    }

    @Test
    void notificationRetentionSkipsOutstandingDeliveryInsteadOfFailingTheReminderLane() throws Exception {
        bootstrap();
        WorkTaskResponse task = createDueTask("delivery retention fence");
        replaceAtDueReminder(task);
        pump.runOnce();
        long notificationId = jdbc.queryForObject("SELECT id FROM crm_notification WHERE tenant_id = ? AND work_task_id = ?",
                Long.class, TENANT, task.getId());
        insertReadyDelivery(notificationId, task.getId(), deliveryStore.currentUtc());
        jdbc.update("UPDATE crm_notification SET created_time = UTC_TIMESTAMP(6) - INTERVAL 100 DAY WHERE id = ?",
                notificationId);

        pump.runOnce();

        assertEquals(1L, count("SELECT COUNT(*) FROM crm_notification WHERE id = ?", notificationId));
        assertEquals(1L, count("SELECT COUNT(*) FROM crm_notification_delivery WHERE notification_id = ?", notificationId));
    }

    @Test
    void preferenceRoutesPassTheRealSecurityFilterChainForAnAuthorizedCaller() throws Exception {
        bootstrap();

        performAs(OWNER, TENANT, get("/api/v1/notifications/preferences"))
                .andExpect(status().isOk());
        performAs(OWNER, TENANT, put("/api/v1/notifications/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new NotificationPreferenceUpdateRequest().setEmailEnabled(true))))
                .andExpect(status().isOk());
    }

    @Test
    void disabledEmailChannelCreatesNoDeliveryAndStillMaintainsLegacyRowsToADeadline() throws Exception {
        bootstrap();
        jdbc.update("INSERT INTO crm_notification_preference (tenant_id, recipient_actor_id, email_enabled) "
                        + "VALUES (?, ?, 1)", TENANT, OWNER);
        WorkTaskResponse task = createDueTask("disabled delivery channel");
        replaceAtDueReminder(task);
        pump.runOnce();
        assertEquals(0L, count("SELECT COUNT(*) FROM crm_notification_delivery WHERE tenant_id = ? AND work_task_id = ?",
                TENANT, task.getId()));

        long notificationId = jdbc.queryForObject("SELECT id FROM crm_notification WHERE tenant_id = ? AND work_task_id = ?",
                Long.class, TENANT, task.getId());
        LocalDateTime past = deliveryStore.currentUtc().minusDays(2);
        insertReadyDelivery(notificationId, task.getId(), past);

        deliveryPump.runOnce();

        assertEquals("DEAD", jdbc.queryForObject("SELECT status FROM crm_notification_delivery WHERE notification_id = ?",
                String.class, notificationId));
    }

    @Test
    void deliveryDeadlineRecoveryNeverWritesAvailableAtAtOrPastTheDeadline() throws Exception {
        bootstrap();
        WorkTaskResponse task = createDueTask("delivery deadline recovery");
        replaceAtDueReminder(task);
        pump.runOnce();
        long notificationId = jdbc.queryForObject("SELECT id FROM crm_notification WHERE tenant_id = ? AND work_task_id = ?",
                Long.class, TENANT, task.getId());
        LocalDateTime expiredNow = deliveryStore.currentUtc();
        long expiredLease = insertLeasedDelivery(notificationId, task.getId(), expiredNow.minusMinutes(2),
                expiredNow.minusSeconds(1), expiredNow.minusSeconds(1), UUID.randomUUID(), 1L);

        var claimed = deliveryStore.claimDue(UUID.randomUUID(), deliveryStore.currentUtc(), Duration.ofMinutes(2), 5);
        assertEquals(1, claimed.deadCount());
        assertEquals("DEAD", jdbc.queryForObject("SELECT status FROM crm_notification_delivery WHERE id = ?",
                String.class, expiredLease));

        WorkTaskResponse retryTask = createDueTask("delivery retry deadline recovery");
        replaceAtDueReminder(retryTask);
        pump.runOnce();
        long retryNotificationId = jdbc.queryForObject(
                "SELECT id FROM crm_notification WHERE tenant_id = ? AND work_task_id = ?", Long.class, TENANT,
                retryTask.getId());
        LocalDateTime retryNow = deliveryStore.currentUtc();
        UUID leaseOwner = UUID.randomUUID();
        long nearDeadlineLease = insertLeasedDelivery(retryNotificationId, retryTask.getId(), retryNow.minusMinutes(1),
                retryNow.plusSeconds(1), retryNow.plusMinutes(1), leaseOwner, 7L);
        assertEquals(io.github.archetom.crm.application.port.out.notification.NotificationDeliveryStatus.DEAD,
                deliveryStore.recoverNoDispatchFailure(new io.github.archetom.crm.domain.valueobject.TenantId(TENANT),
                        nearDeadlineLease, leaseOwner, 7L, deliveryStore.currentUtc(), 5, Duration.ofMinutes(1))
                        .orElseThrow());
        assertEquals(1L, count("SELECT COUNT(*) FROM crm_notification_delivery "
                + "WHERE id = ? AND deadline_at > available_at", nearDeadlineLease));
    }

    @Test
    void idempotentDispatchUsesItsOriginalWindowAndNeverSlidesItOnRetry() throws Exception {
        bootstrap();
        WorkTaskResponse task = createDueTask("idempotent delivery window");
        replaceAtDueReminder(task);
        pump.runOnce();
        long notificationId = jdbc.queryForObject("SELECT id FROM crm_notification WHERE tenant_id = ? AND work_task_id = ?",
                Long.class, TENANT, task.getId());
        LocalDateTime now = deliveryStore.currentUtc();
        UUID leaseOwner = UUID.randomUUID();
        long deliveryId = insertLeasedDelivery(notificationId, task.getId(), now.minusSeconds(1),
                now.plusDays(1), now.plusMinutes(1), leaseOwner, 9L);
        LocalDateTime originalWindow = now.plusSeconds(1);

        assertTrue(deliveryStore.beginDispatch(new TenantId(TENANT), deliveryId, leaseOwner, 9L,
                NotificationDispatchGuarantee.idempotent(Duration.ofSeconds(1)), originalWindow, "a".repeat(64),
                "TEST_KEY_V1", now));
        assertEquals(NotificationDeliveryStatus.AMBIGUOUS,
                deliveryStore.settleDispatch(new TenantId(TENANT), deliveryId, leaseOwner, 9L,
                        NotificationDispatchOutcome.AMBIGUOUS, now, 5, Duration.ofMinutes(1)).orElseThrow());
        assertEquals(1L, count("SELECT COUNT(*) FROM crm_notification_delivery "
                        + "WHERE id = ? AND status = 'AMBIGUOUS' AND idempotency_until = ? "
                        + "AND endpoint_digest = ? AND endpoint_digest_version = ?", deliveryId,
                Timestamp.valueOf(originalWindow), "a".repeat(64), "TEST_KEY_V1"));
    }

    @Test
    void idempotentAmbiguityStaysAmbiguousWhenItsRetryDeadlineOrRecoveryBudgetExpires() throws Exception {
        bootstrap();
        WorkTaskResponse task = createDueTask("idempotent ambiguity certainty");
        replaceAtDueReminder(task);
        pump.runOnce();
        long notificationId = jdbc.queryForObject("SELECT id FROM crm_notification WHERE tenant_id = ? AND work_task_id = ?",
                Long.class, TENANT, task.getId());
        LocalDateTime now = deliveryStore.currentUtc();
        UUID leaseOwner = UUID.randomUUID();
        long deliveryId = insertLeasedDelivery(notificationId, task.getId(), now.minusMinutes(1),
                now.plusDays(1), now.plusMinutes(1), leaseOwner, 11L);

        assertTrue(deliveryStore.beginDispatch(new TenantId(TENANT), deliveryId, leaseOwner, 11L,
                NotificationDispatchGuarantee.idempotent(Duration.ofHours(1)), now.plusHours(1), "c".repeat(64),
                "TEST_KEY_V1", now));
        assertEquals(NotificationDeliveryStatus.RETRY_WAIT,
                deliveryStore.settleDispatch(new TenantId(TENANT), deliveryId, leaseOwner, 11L,
                        NotificationDispatchOutcome.AMBIGUOUS, now, 5, Duration.ofMinutes(1)).orElseThrow());
        jdbc.update("UPDATE crm_notification_delivery SET available_at = UTC_TIMESTAMP(6) - INTERVAL 2 SECOND, "
                        + "deadline_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND "
                        + "WHERE id = ?", deliveryId);

        var expired = deliveryStore.claimDue(UUID.randomUUID(), deliveryStore.currentUtc(), Duration.ofMinutes(2), 5);
        assertEquals(1, expired.ambiguousCount());
        assertEquals(0, expired.deadCount());
        assertEquals("AMBIGUOUS", jdbc.queryForObject("SELECT status FROM crm_notification_delivery WHERE id = ?",
                String.class, deliveryId));

        WorkTaskResponse recoveryTask = createDueTask("idempotent ambiguity recovery");
        replaceAtDueReminder(recoveryTask);
        pump.runOnce();
        long recoveryNotificationId = jdbc.queryForObject(
                "SELECT id FROM crm_notification WHERE tenant_id = ? AND work_task_id = ?", Long.class, TENANT,
                recoveryTask.getId());
        LocalDateTime recoveryNow = deliveryStore.currentUtc();
        UUID recoveryOwner = UUID.randomUUID();
        long recoveryDeliveryId = insertLeasedDelivery(recoveryNotificationId, recoveryTask.getId(),
                recoveryNow.minusMinutes(1), recoveryNow.plusMinutes(5), recoveryNow.plusMinutes(1), recoveryOwner, 12L);
        assertTrue(deliveryStore.beginDispatch(new TenantId(TENANT), recoveryDeliveryId, recoveryOwner, 12L,
                NotificationDispatchGuarantee.idempotent(Duration.ofHours(1)), recoveryNow.plusHours(1), "d".repeat(64),
                "TEST_KEY_V1", recoveryNow));
        assertEquals(NotificationDeliveryStatus.RETRY_WAIT,
                deliveryStore.settleDispatch(new TenantId(TENANT), recoveryDeliveryId, recoveryOwner, 12L,
                        NotificationDispatchOutcome.AMBIGUOUS, recoveryNow, 5, Duration.ofSeconds(1)).orElseThrow());
        UUID admissionOwner = UUID.randomUUID();
        jdbc.update("UPDATE crm_notification_delivery SET status = 'LEASED', available_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND, "
                        + "lease_owner = ?, lease_fence = 13, "
                        + "lease_until = UTC_TIMESTAMP(6) + INTERVAL 1 SECOND, deadline_at = UTC_TIMESTAMP(6) + INTERVAL 1 SECOND "
                        + "WHERE id = ?", admissionOwner.toString(), recoveryDeliveryId);

        assertEquals(NotificationDeliveryStatus.AMBIGUOUS,
                deliveryStore.recoverNoDispatchFailure(new TenantId(TENANT), recoveryDeliveryId, admissionOwner, 13L,
                        deliveryStore.currentUtc(), 5, Duration.ofMinutes(1)).orElseThrow());
    }

    @Test
    void taskMutationCancelsStalePendingOccurrencesBeforeCreatingTheNewProjection() throws Exception {
        bootstrap();
        WorkTaskResponse task = createDueTask("stale pending projection");
        replaceAtDueReminder(task);
        long oldProjection = jdbc.queryForObject("SELECT notification_projection_version FROM crm_work_task "
                + "WHERE tenant_id = ? AND id = ?", Long.class, TENANT, task.getId());
        assertEquals("READY", occurrenceStatus(task.getId()));

        WorkTaskResponse current = task(task.getId());
        response(performAs(OWNER, TENANT, put("/api/v1/work/tasks/{id}", task.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new WorkTaskUpdateRequest().setTitle("stale pending projection changed")
                        .setDescription(current.getDescription()).setPriority(current.getPriority())
                        .setDueTime(current.getDueTime()).setVersion(current.getVersion()))))
                .andExpect(status().isOk()));

        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM crm_work_task_reminder_occurrence "
                        + "WHERE tenant_id = ? AND work_task_id = ? AND notification_projection_version = ?",
                String.class, TENANT, task.getId(), oldProjection));
        assertEquals(1L, count("SELECT COUNT(*) FROM crm_work_task_reminder_occurrence "
                        + "WHERE tenant_id = ? AND work_task_id = ? AND status = 'READY'",
                TENANT, task.getId()));
        assertEquals(0L, count("SELECT COUNT(*) FROM crm_notification WHERE tenant_id = ? AND work_task_id = ?",
                TENANT, task.getId()));
    }

    @Test
    void taskMutationAndReminderEffectUseTheSameTaskThenOccurrenceLockOrder() throws Exception {
        bootstrap();
        WorkTaskResponse task = createDueTask("lock order evidence");
        replaceAtDueReminder(task);
        WorkTaskResponse current = task(task.getId());

        List<Integer> results = concurrently(
                () -> {
                    pump.runOnce();
                    return 200;
                },
                () -> performAs(OWNER, TENANT, put("/api/v1/work/tasks/{id}", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WorkTaskUpdateRequest().setTitle("lock order changed")
                                .setDescription(current.getDescription()).setPriority(current.getPriority())
                                .setDueTime(current.getDueTime()).setVersion(current.getVersion()))))
                        .andReturn().getResponse().getStatus());

        assertEquals(List.of(200, 200), results.stream().sorted().toList());
        assertEquals(0L, count("SELECT COUNT(*) FROM crm_work_task_reminder_occurrence "
                        + "WHERE tenant_id = ? AND work_task_id = ? AND status = 'LEASED'",
                TENANT, task.getId()));
    }

    @Test
    void inboxVisibilityRequiresActiveMembershipCurrentParticipationAndCapabilities() throws Exception {
        bootstrap();
        provisionInboxMember();
        WorkTaskResponse task = createDueTask("recipient visibility", RECIPIENT);
        replaceAtDueReminder(task);
        pump.runOnce();
        long notificationId = jdbc.queryForObject("SELECT id FROM crm_notification WHERE tenant_id = ? "
                + "AND work_task_id = ? AND recipient_actor_id = ?", Long.class, TENANT, task.getId(), RECIPIENT);

        assertEquals(1L, inboxTotal(RECIPIENT));
        assertEquals(200, markReadAs(RECIPIENT, notificationId));

        WorkTaskResponse current = task(task.getId());
        response(performAs(OWNER, TENANT, put("/api/v1/work/tasks/{id}/assignee", task.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new WorkTaskAssigneeUpdateRequest().setAssigneeActorId(OWNER)
                        .setVersion(current.getVersion()))))
                .andExpect(status().isOk()));
        // READ receipts remain durable, but a user who is no longer a task participant
        // cannot count, page, or read that old projection.
        assertEquals("READ", jdbc.queryForObject("SELECT status FROM crm_notification WHERE id = ?",
                String.class, notificationId));
        assertEquals(0L, inboxTotal(RECIPIENT));
        assertEquals(404, markReadAs(RECIPIENT, notificationId));

        jdbc.update("UPDATE organization_membership SET status = 'SUSPENDED' WHERE tenant_id = ? AND actor_id = ?",
                TENANT, RECIPIENT);
        performAs(RECIPIENT, TENANT, get("/api/v1/notifications").param("page", "1").param("size", "10"))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE organization_membership SET status = 'ACTIVE' WHERE tenant_id = ? AND actor_id = ?",
                TENANT, RECIPIENT);
        jdbc.update("DELETE FROM authorization_role_grant WHERE tenant_id = ? AND role_id = ? "
                        + "AND authority_code = 'notifications:read'", TENANT, inboxRoleId());
        performAs(RECIPIENT, TENANT, get("/api/v1/notifications").param("page", "1").param("size", "10"))
                .andExpect(status().isForbidden());
    }

    @Test
    void overdueReminderCreatesOnlyAnInboxEffectAndDoesNotMutateTaskOrWorklist() throws Exception {
        bootstrap();
        WorkTaskResponse task = createDueTask("overdue stays a reminder");
        replaceOverdueReminder(task);
        long versionBefore = jdbc.queryForObject("SELECT version FROM crm_work_task WHERE tenant_id = ? AND id = ?",
                Long.class, TENANT, task.getId());
        long worklistBefore = jdbc.queryForObject("SELECT worklist_projection_version FROM crm_work_task "
                + "WHERE tenant_id = ? AND id = ?", Long.class, TENANT, task.getId());
        long acknowledgementsBefore = count("SELECT COUNT(*) FROM worklist_acknowledgement WHERE tenant_id = ?", TENANT);

        pump.runOnce();

        assertEquals("WORK_TASK_ESCALATION", jdbc.queryForObject("SELECT kind FROM crm_notification "
                + "WHERE tenant_id = ? AND work_task_id = ?", String.class, TENANT, task.getId()));
        assertEquals("TODO", jdbc.queryForObject("SELECT status FROM crm_work_task WHERE tenant_id = ? AND id = ?",
                String.class, TENANT, task.getId()));
        assertEquals(versionBefore, jdbc.queryForObject("SELECT version FROM crm_work_task WHERE tenant_id = ? AND id = ?",
                Long.class, TENANT, task.getId()));
        assertEquals(worklistBefore, jdbc.queryForObject("SELECT worklist_projection_version FROM crm_work_task "
                + "WHERE tenant_id = ? AND id = ?", Long.class, TENANT, task.getId()));
        assertEquals(acknowledgementsBefore, count("SELECT COUNT(*) FROM worklist_acknowledgement WHERE tenant_id = ?", TENANT));
    }

    @Test
    void taskAndSeriesReminderPlansRejectDerivedTimesOutsideMySqlDatetime() throws Exception {
        bootstrap();
        WorkTaskResponse task = createTask("upper task boundary", OWNER,
                LocalDateTime.of(9999, 12, 31, 23, 59, 59));
        ResultActions rejectedTask = performAs(OWNER, TENANT, put("/api/v1/work/tasks/{id}/reminders", task.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(atDuePlan(task.getVersion()))));
        rejectedTask.andExpect(status().isUnprocessableContent());

        WorkTaskSeriesMutationResultResponse series = seriesResponse(performAs(OWNER, TENANT, post("/api/v1/work/task-series")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new WorkTaskSeriesCreateRequest().setTitle("upper series boundary")
                        .setAssigneeActorId(OWNER).setPriority("NORMAL")
                        .setRecurrenceFrequency("DAILY").setRecurrenceInterval(1).setZoneId("UTC")
                        .setStartLocalDate(LocalDate.of(9999, 12, 31)).setLocalTime(LocalTime.of(23, 59))))));
        ResultActions rejectedSeries = performAs(OWNER, TENANT, put("/api/v1/work/task-series/{id}/reminders", series.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(atDuePlan(series.version()))));
        rejectedSeries.andExpect(status().isUnprocessableContent());
    }

    private void bootstrap() throws Exception {
        performAs(OWNER, TENANT, post("/api/v1/organization/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName("Reminder owner"))))
                .andExpect(status().isOk());
    }

    private WorkTaskResponse createDueTask(String title) throws Exception {
        return createDueTask(title, OWNER);
    }

    private WorkTaskResponse createDueTask(String title, long assignee) throws Exception {
        return createTask(title, assignee, LocalDateTime.now(Clock.systemUTC()).minusMinutes(2));
    }

    private WorkTaskResponse createTask(String title, long assignee, LocalDateTime dueTime) throws Exception {
        ResultActions action = performAs(OWNER, TENANT, post("/api/v1/work/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new WorkTaskCreateRequest().setTitle(title).setAssigneeActorId(assignee)
                        .setDueTime(dueTime))));
        return response(action.andExpect(status().isOk()));
    }

    private WorkTaskReminderPlanResponse replaceAtDueReminder(WorkTaskResponse task) throws Exception {
        ResultActions action = performAs(OWNER, TENANT, put("/api/v1/work/tasks/{id}/reminders", task.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(atDuePlan(task.getVersion()))));
        return fromJson(action.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                WorkTaskReminderPlanResponse.class);
    }

    private void replaceOverdueReminder(WorkTaskResponse task) throws Exception {
        WorkTaskReminderPlanUpdateRequest plan = new WorkTaskReminderPlanUpdateRequest().setVersion(task.getVersion())
                .setSteps(List.of(new WorkTaskReminderStepRequest().setTiming("OVERDUE")
                        .setOffsetMinutes(1).setRecipientRoles(List.of("ASSIGNEE"))));
        ResultActions action = performAs(OWNER, TENANT, put("/api/v1/work/tasks/{id}/reminders", task.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(plan)));
        action.andExpect(status().isOk());
    }

    private WorkTaskReminderPlanUpdateRequest atDuePlan(long version) {
        return new WorkTaskReminderPlanUpdateRequest().setVersion(version)
                .setSteps(List.of(new WorkTaskReminderStepRequest().setTiming("AT_DUE")
                        .setOffsetMinutes(0).setRecipientRoles(List.of("ASSIGNEE"))));
    }

    private WorkTaskResponse task(long taskId) throws Exception {
        return response(performAs(OWNER, TENANT, get("/api/v1/work/tasks/{id}", taskId))
                .andExpect(status().isOk()));
    }

    private int markRead(long notificationId) throws Exception {
        return markReadAs(OWNER, notificationId);
    }

    private int markReadAs(long actorId, long notificationId) throws Exception {
        return performAs(actorId, TENANT, post("/api/v1/notifications/{id}/read", notificationId)
                .contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse().getStatus();
    }

    private long inboxTotal(long actorId) throws Exception {
        String body = performAs(actorId, TENANT, get("/api/v1/notifications")
                .param("page", "1").param("size", "10"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("totalNum").asLong();
    }

    private void provisionInboxMember() {
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                RECIPIENT, "Reminder recipient");
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')",
                TENANT, RECIPIENT);
        jdbc.update("INSERT INTO authorization_role (tenant_id, name, description, status) VALUES (?, ?, ?, 'ACTIVE')",
                TENANT, "Reminder inbox recipient", "B27b inbox acceptance fixture");
        long roleId = inboxRoleId();
        jdbc.update("INSERT INTO authorization_role_grant (tenant_id, role_id, authority_code) VALUES (?, ?, 'notifications:read')",
                TENANT, roleId);
        jdbc.update("INSERT INTO authorization_role_grant (tenant_id, role_id, authority_code) VALUES (?, ?, 'work-tasks:read')",
                TENANT, roleId);
        jdbc.update("INSERT INTO authorization_role_assignment (tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT, RECIPIENT, roleId);
    }

    private long inboxRoleId() {
        return jdbc.queryForObject("SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT, "Reminder inbox recipient");
    }

    private void insertReadyDelivery(long notificationId, long taskId, LocalDateTime now) {
        jdbc.update("INSERT INTO crm_notification_delivery (tenant_id, source_kind, notification_id, recipient_actor_id, work_task_id, "
                        + "channel, template_revision, payload_digest, status, attempt_count, available_at, deadline_at, lease_fence) "
                        + "VALUES (?, 'WORK_TASK_NOTIFICATION', ?, ?, ?, 'EMAIL', 'B27C_EMAIL_V1', ?, 'READY', 0, ?, ?, 0)",
                TENANT, notificationId, OWNER, taskId, "a".repeat(64), Timestamp.valueOf(now),
                Timestamp.valueOf(now.plusDays(1)));
    }

    private long insertLeasedDelivery(long notificationId, long taskId, LocalDateTime availableAt,
                                      LocalDateTime deadlineAt, LocalDateTime leaseUntil,
                                      UUID leaseOwner, long leaseFence) {
        jdbc.update("INSERT INTO crm_notification_delivery (tenant_id, source_kind, notification_id, recipient_actor_id, work_task_id, "
                        + "channel, template_revision, payload_digest, status, attempt_count, available_at, deadline_at, "
                        + "lease_owner, lease_fence, lease_until) "
                        + "VALUES (?, 'WORK_TASK_NOTIFICATION', ?, ?, ?, 'EMAIL', 'B27C_EMAIL_V1', ?, 'LEASED', 1, ?, ?, ?, ?, ?)",
                TENANT, notificationId, OWNER, taskId, "b".repeat(64), Timestamp.valueOf(availableAt),
                Timestamp.valueOf(deadlineAt), leaseOwner.toString(), leaseFence, Timestamp.valueOf(leaseUntil));
        return jdbc.queryForObject("SELECT id FROM crm_notification_delivery WHERE tenant_id = ? AND notification_id = ?",
                Long.class, TENANT, notificationId);
    }

    private String occurrenceStatus(long taskId) {
        return jdbc.queryForObject("SELECT status FROM crm_work_task_reminder_occurrence "
                + "WHERE tenant_id = ? AND work_task_id = ?", String.class, TENANT, taskId);
    }

    private long count(String sql, Object... parameters) {
        return jdbc.queryForObject(sql, Long.class, parameters);
    }

    private List<Integer> concurrently(Callable<Integer> first, Callable<Integer> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstResult = executor.submit(first);
            Future<Integer> secondResult = executor.submit(second);
            return List.of(firstResult.get(15, TimeUnit.SECONDS), secondResult.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private WorkTaskResponse response(ResultActions action) throws Exception {
        return fromJson(action.andReturn().getResponse().getContentAsString(), WorkTaskResponse.class);
    }

    private WorkTaskSeriesMutationResultResponse seriesResponse(ResultActions action) throws Exception {
        return fromJson(action.andReturn().getResponse().getContentAsString(), WorkTaskSeriesMutationResultResponse.class);
    }

    private ResultActions performAs(long actorId, long tenantId, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("X-Dev-User-Id", actorId).header("X-Dev-Tenant-Id", tenantId));
    }

    @Override
    protected void clearTestData() {
        executeSql("DELETE FROM crm_notification_delivery WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_notification_preference WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_notification WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_work_task_reminder_occurrence WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_work_task_reminder_step WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_work_task_series_reminder_step WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_automation_run WHERE tenant_id = " + TENANT
                + " AND kind = 'WORK_TASK_RECURRENCE'");
        executeSql("DELETE FROM crm_automation_schedule WHERE tenant_id = " + TENANT
                + " AND kind = 'WORK_TASK_RECURRENCE'");
        executeSql("DELETE FROM crm_work_task_series_occurrence WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_work_task_follower WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_work_task_comment WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_work_task WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_work_task_series_follower WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_work_task_series_weekday WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_work_task_series WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM authorization_role_assignment WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM authorization_role_grant WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM authorization_role WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM organization_membership WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM organization_member_profile WHERE actor_id IN (" + OWNER + ", " + RECIPIENT + ")");
        executeSql("DELETE FROM organization_tenant WHERE tenant_id = " + TENANT);
    }
}
