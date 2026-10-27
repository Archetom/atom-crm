package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.automation.AutomationExecutionContext;
import io.github.archetom.crm.application.automation.AutomationScheduleManager;
import io.github.archetom.crm.application.automation.BackgroundWorkObservability;
import io.github.archetom.crm.application.automation.WorkRecurrenceOutcome;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.application.service.WorkTaskRecurrenceService;
import io.github.archetom.crm.application.transaction.AfterCommitExecutor;
import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.entity.WorkTaskSeries;
import io.github.archetom.crm.domain.entity.WorkTaskSeriesOccurrence;
import io.github.archetom.crm.domain.model.WorkTaskOrigin;
import io.github.archetom.crm.domain.model.WorkTaskSeriesOccurrenceStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.WorkTaskRepository;
import io.github.archetom.crm.domain.repository.WorkTaskSeriesRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** B27a effect transaction; it never synthesizes an AuthenticatedCaller. */
@Service
public class WorkTaskRecurrenceServiceImpl implements WorkTaskRecurrenceService {
    private final WorkTaskSeriesRepository seriesRepository;
    private final WorkTaskRepository taskRepository;
    private final OrganizationTenantRepository tenantRepository;
    private final TenantAuthorizationEvaluator authorizationEvaluator;
    private final AutomationScheduleManager scheduleManager;
    private final AfterCommitExecutor afterCommitExecutor;
    private final BackgroundWorkObservability observability;
    private final WorkTaskReminderCoordinator reminderCoordinator;

    @Autowired
    public WorkTaskRecurrenceServiceImpl(
            WorkTaskSeriesRepository seriesRepository, WorkTaskRepository taskRepository,
            OrganizationTenantRepository tenantRepository,
            TenantAuthorizationEvaluator authorizationEvaluator,
            AutomationScheduleManager scheduleManager, AfterCommitExecutor afterCommitExecutor,
            BackgroundWorkObservability observability,
            WorkTaskReminderCoordinator reminderCoordinator) {
        this.seriesRepository = seriesRepository;
        this.taskRepository = taskRepository;
        this.tenantRepository = tenantRepository;
        this.authorizationEvaluator = authorizationEvaluator;
        this.scheduleManager = scheduleManager;
        this.afterCommitExecutor = afterCommitExecutor;
        this.observability = observability;
        this.reminderCoordinator = reminderCoordinator;
    }

    /** Compatibility constructor retained for the B27a unit seam. */
    public WorkTaskRecurrenceServiceImpl(
            WorkTaskSeriesRepository seriesRepository, WorkTaskRepository taskRepository,
            OrganizationTenantRepository tenantRepository,
            TenantAuthorizationEvaluator authorizationEvaluator,
            AutomationScheduleManager scheduleManager, AfterCommitExecutor afterCommitExecutor,
            BackgroundWorkObservability observability) {
        this(seriesRepository, taskRepository, tenantRepository, authorizationEvaluator, scheduleManager,
                afterCommitExecutor, observability, null);
    }

    @Override
    public int materialize(AutomationExecutionContext context, int limit,
                           Supplier<LocalDateTime> committedTimeSupplier) {
        if (context == null || committedTimeSupplier == null || limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("work recurrence automation context is invalid");
        }
        TenantId tenantId = context.tenantId();
        LocalDateTime evaluationUtc = context.evaluationTime();
        if (!tenantRepository.lockForTenantChanges(tenantId)) return 0;
        // The supplied effect fence takes the recurrence control and exact run
        // locks after the tenant lock, before any series lock is acquired.
        LocalDateTime committedTime = committedTimeSupplier.get();
        if (committedTime == null) throw new IllegalStateException("work recurrence database time is unavailable");
        int materialized = 0;
        long skipped = 0;
        long paused = 0;
        boolean activeStateChanged = false;
        List<WorkTaskSeries> dueSeries = seriesRepository.findDueForUpdate(tenantId, evaluationUtc, limit);
        // Participants repeat heavily across series, and each evaluation costs its own queries.
        // Resolving the union once keeps the authorization cost independent of the batch size.
        Map<MemberId, TenantAuthorizationEvaluator.Evaluation> authorization =
                authorizationEvaluator.evaluateAll(tenantId, dueSeries.stream()
                        .flatMap(series -> participants(series).stream())
                        .distinct().toList());
        for (WorkTaskSeries series : dueSeries) {
            if (!canCreate(authorization.get(series.getCreatedByMemberId()))) {
                series.pauseForSafety(committedTime, "CREATOR_ACCESS_REVOKED");
                seriesRepository.save(tenantId, series);
                paused++;
                activeStateChanged = true;
                continue;
            }
            if (!canCreate(authorization.get(series.getCurrentAssigneeMemberId()))) {
                series.pauseForSafety(committedTime, "ASSIGNEE_ACCESS_REVOKED");
                seriesRepository.save(tenantId, series);
                paused++;
                activeStateChanged = true;
                continue;
            }
            WorkTaskSeries.DueOccurrence due = series.advanceToLatestDue(evaluationUtc, committedTime);
            if (due == null) {
                if (series.getStatus() != io.github.archetom.crm.domain.model.WorkTaskSeriesStatus.ACTIVE) {
                    seriesRepository.save(tenantId, series);
                    activeStateChanged = true;
                }
                continue;
            }
            if (seriesRepository.hasOpenGeneratedTask(tenantId, series.getId())) {
                series.recordOpenSkip(committedTime);
                seriesRepository.appendOccurrence(tenantId, skipped(series, due, committedTime, "OPEN_TASK"));
                seriesRepository.save(tenantId, series);
                skipped += due.coalescedSkippedCount() + 1;
                activeStateChanged |= series.getStatus()
                        != io.github.archetom.crm.domain.model.WorkTaskSeriesStatus.ACTIVE;
                continue;
            }
            List<MemberId> followers = series.getFollowerMemberIds().stream()
                    .filter(member -> canRead(authorization.get(member))).toList();
            WorkTaskOrigin origin = WorkTaskOrigin.recurrence(series.getId().getValue(), due.generation(), due.sequence(),
                    due.resolution().nominalLocalTime(), series.getRecurrenceRule().zoneId(),
                    due.resolution().offsetSeconds(), due.resolution().resolvedUtcTime());
            WorkTask task = WorkTask.createRecurring(tenantId, series.getCreatedByMemberId(),
                    series.getCurrentAssigneeMemberId(), followers, series.getTitle(), series.getDescription(),
                    series.getPriority(), due.resolution().resolvedUtcTime(), origin, committedTime);
            if (!series.getReminderPlan().isEmpty()) {
                task.replaceReminderPlan(series.getReminderPlan(), committedTime);
            }
            if (reminderCoordinator != null) {
                reminderCoordinator.validateForPersistence(task);
            }
            taskRepository.save(tenantId, task);
            if (reminderCoordinator != null) {
                reminderCoordinator.synchronize(tenantId, task, committedTime);
            }
            seriesRepository.appendOccurrence(tenantId, new WorkTaskSeriesOccurrence(null, tenantId, series.getId(),
                    due.generation(), due.sequence(), WorkTaskSeriesOccurrenceStatus.MATERIALIZED,
                    due.resolution().nominalLocalTime(), series.getRecurrenceRule().zoneId(),
                    due.resolution().offsetSeconds(), due.resolution().resolvedUtcTime(), task.getId(), null, committedTime));
            seriesRepository.save(tenantId, series);
            activeStateChanged |= series.getStatus()
                    != io.github.archetom.crm.domain.model.WorkTaskSeriesStatus.ACTIVE;
            materialized++;
            skipped += due.coalescedSkippedCount();
        }
        if (activeStateChanged) {
            scheduleManager.synchronizeCurrentRun(context,
                    seriesRepository.hasActive(tenantId));
        }
        recordOutcomes(materialized, skipped, paused);
        return materialized;
    }

    private void recordOutcomes(long materialized, long skipped, long paused) {
        if (materialized < 1 && skipped < 1 && paused < 1) return;
        afterCommitExecutor.execute("work recurrence outcome metrics", () -> {
            if (materialized > 0) {
                observability.recordWorkRecurrenceOutcome(WorkRecurrenceOutcome.MATERIALIZED, materialized);
            }
            if (skipped > 0) {
                observability.recordWorkRecurrenceOutcome(WorkRecurrenceOutcome.SKIPPED, skipped);
            }
            if (paused > 0) {
                observability.recordWorkRecurrenceOutcome(WorkRecurrenceOutcome.PAUSED, paused);
            }
        });
    }

    private WorkTaskSeriesOccurrence skipped(WorkTaskSeries series, WorkTaskSeries.DueOccurrence due,
                                              LocalDateTime committedTime, String reason) {
        return new WorkTaskSeriesOccurrence(null, series.getTenantId(), series.getId(), due.generation(), due.sequence(),
                WorkTaskSeriesOccurrenceStatus.SKIPPED, due.resolution().nominalLocalTime(),
                series.getRecurrenceRule().zoneId(), due.resolution().offsetSeconds(),
                due.resolution().resolvedUtcTime(), null, reason, committedTime);
    }

    private static Collection<MemberId> participants(WorkTaskSeries series) {
        java.util.LinkedHashSet<MemberId> values = new java.util.LinkedHashSet<>();
        values.add(series.getCreatedByMemberId()); values.add(series.getCurrentAssigneeMemberId()); values.addAll(series.getFollowerMemberIds());
        return values;
    }
    private static boolean canCreate(TenantAuthorizationEvaluator.Evaluation evaluation) {
        return canRead(evaluation) && has(evaluation, AuthorityCatalog.WORK_TASKS_WRITE);
    }
    private static boolean canRead(TenantAuthorizationEvaluator.Evaluation evaluation) {
        return evaluation != null && evaluation.activeMember() && has(evaluation, AuthorityCatalog.WORK_TASKS_READ);
    }
    private static boolean has(TenantAuthorizationEvaluator.Evaluation evaluation, String authority) {
        return evaluation != null && evaluation.effectiveGrants().stream()
                .anyMatch(grant -> authority.equals(grant.authorityCode().getValue()));
    }
}
