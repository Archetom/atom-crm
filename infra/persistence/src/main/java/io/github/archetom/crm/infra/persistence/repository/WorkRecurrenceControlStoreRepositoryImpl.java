package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.application.automation.WorkRecurrencePolicy;
import io.github.archetom.crm.application.automation.WorkRecurrenceReadiness;
import io.github.archetom.crm.application.automation.AutomationKind;
import io.github.archetom.crm.application.automation.AutomationScheduleManager;
import io.github.archetom.crm.application.port.out.automation.WorkRecurrenceControlStore;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkRecurrenceControlMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AutomationScheduleMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkTaskSeriesMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkTaskSeriesWeekdayMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkRecurrenceControlPO;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskSeriesPO;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskSeriesWeekdayPO;
import io.github.archetom.crm.domain.exception.WorkRecurrenceTimeRangeExceededException;
import io.github.archetom.crm.domain.model.WorkRecurrenceFrequency;
import io.github.archetom.crm.domain.model.WorkRecurrenceRule;
import io.github.archetom.crm.domain.model.WorkRecurrenceResolution;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** One keyed row freezes recurrence semantics across mixed replicas. */
@Repository
@RequiredArgsConstructor
public class WorkRecurrenceControlStoreRepositoryImpl implements WorkRecurrenceControlStore {
    private static final int RECONCILIATION_BATCH_SIZE = 100;

    private final WorkRecurrenceControlMapper controlMapper;
    private final AutomationScheduleMapper scheduleMapper;
    private final WorkTaskSeriesMapper seriesMapper;
    private final WorkTaskSeriesWeekdayMapper weekdayMapper;
    private final AutomationScheduleManager scheduleManager;
    private final PlatformTransactionManager transactionManager;

    @Override
    public WorkRecurrenceReadiness reconcile(WorkRecurrencePolicy policy) {
        WorkRecurrenceReadiness immediate = immediate(policy);
        if (immediate != null) return immediate;
        return requiresNew().execute(status -> {
            LocalDateTime now = utcNow();
            WorkRecurrenceControlPO control = controlMapper.selectForUpdate();
            if (control == null) {
                controlMapper.insertIfAbsent(policy.tzdbFingerprint(), policy.policyRevision(), now);
                control = controlMapper.selectForUpdate();
            }
            if (control == null) throw new IllegalStateException("work recurrence control is unavailable");
            if (isReconciling(control)) {
                long targetRevision = revisionNumber(control.getTargetPolicyRevision());
                if (policy.revisionNumber() < targetRevision) return WorkRecurrenceReadiness.REVISION_DRIFT;
                if (policy.revisionNumber() > targetRevision) {
                    // No effect may pass requireCurrent while reconciliation is in flight,
                    // so a newer policy can safely discard the older checkpoint and restart.
                    startReconciliation(control, policy);
                    return reconcileOneBatch(control, policy, now);
                }
                return targetMatches(control, policy)
                        ? reconcileOneBatch(control, policy, now) : WorkRecurrenceReadiness.RECONCILING;
            }
            requireReadyStatus(control);
            long storedRevision = revisionNumber(control.getPolicyRevision());
            if (policy.revisionNumber() < storedRevision) return WorkRecurrenceReadiness.REVISION_DRIFT;
            if (policy.revisionNumber() > storedRevision) {
                startReconciliation(control, policy);
                return reconcileOneBatch(control, policy, now);
            }
            return policy.tzdbFingerprint().equals(control.getTzdbFingerprint())
                    ? WorkRecurrenceReadiness.READY : WorkRecurrenceReadiness.TZDB_DRIFT;
        });
    }

    @Override
    public WorkRecurrenceReadiness readiness(WorkRecurrencePolicy policy) {
        WorkRecurrenceReadiness immediate = immediate(policy);
        if (immediate != null) return immediate;
        WorkRecurrenceControlPO control = controlMapper.select();
        if (control == null) return WorkRecurrenceReadiness.NOT_RECONCILED;
        if (isReconciling(control)) return WorkRecurrenceReadiness.RECONCILING;
        requireReadyStatus(control);
        long storedRevision = revisionNumber(control.getPolicyRevision());
        if (policy.revisionNumber() != storedRevision) return WorkRecurrenceReadiness.REVISION_DRIFT;
        return policy.tzdbFingerprint().equals(control.getTzdbFingerprint())
                ? WorkRecurrenceReadiness.READY : WorkRecurrenceReadiness.TZDB_DRIFT;
    }

    @Override
    public void requireCurrent(WorkRecurrencePolicy policy) {
        WorkRecurrenceReadiness immediate = immediate(policy);
        if (immediate != null) throw new IllegalStateException("work recurrence is not ready");
        WorkRecurrenceControlPO control = controlMapper.selectForUpdate();
        if (control == null || !isReady(control) || revisionNumber(control.getPolicyRevision()) != policy.revisionNumber()
                || !policy.tzdbFingerprint().equals(control.getTzdbFingerprint())) {
            throw new IllegalStateException("work recurrence control drifted");
        }
    }

    private WorkRecurrenceReadiness immediate(WorkRecurrencePolicy policy) {
        if (policy == null) return WorkRecurrenceReadiness.CONFIG_INVALID;
        if (!policy.enabled()) return WorkRecurrenceReadiness.DISABLED;
        return policy.valid() ? null : WorkRecurrenceReadiness.CONFIG_INVALID;
    }

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private LocalDateTime utcNow() {
        LocalDateTime now = scheduleMapper.selectUtcNow();
        if (now == null) throw new IllegalStateException("work recurrence database UTC is unavailable");
        return now;
    }

    /**
     * The fixed control row stays RECONCILING until every bounded checkpointed
     * batch has updated its persisted UTC cursor. Schedule rows are locked before
     * the candidate roots, matching tenant schedule/series command ordering.
     */
    private WorkRecurrenceReadiness reconcileOneBatch(
            WorkRecurrenceControlPO control, WorkRecurrencePolicy policy, LocalDateTime now) {
        List<WorkTaskSeriesPO> candidates = seriesMapper.selectActiveForRecurrenceReconciliation(
                control.getCursorTenantId(), control.getCursorSeriesId(), RECONCILIATION_BATCH_SIZE);
        if (candidates.isEmpty()) return publishReady(policy, now);

        candidates.stream().map(WorkTaskSeriesPO::getTenantId).distinct().sorted().forEach(tenantId ->
                scheduleManager.preparePoolConfiguration(new TenantId(tenantId),
                        AutomationKind.WORK_TASK_RECURRENCE, true));
        List<Long> candidateIds = candidates.stream().map(WorkTaskSeriesPO::getId).toList();
        List<WorkTaskSeriesPO> activeSeries = seriesMapper
                .selectActiveForRecurrenceReconciliationForUpdate(candidateIds);
        Map<Long, Set<DayOfWeek>> weekdays = weekdayMapper.selectForRecurrenceReconciliation(candidateIds).stream()
                .collect(Collectors.groupingBy(WorkTaskSeriesWeekdayPO::getWorkTaskSeriesId,
                        Collectors.mapping(row -> DayOfWeek.of(row.getWeekday()), Collectors.toUnmodifiableSet())));
        Set<Long> endedTenantIds = new java.util.TreeSet<>();
        for (WorkTaskSeriesPO series : activeSeries) {
            try {
                WorkRecurrenceRule rule = new WorkRecurrenceRule(
                        WorkRecurrenceFrequency.valueOf(series.getRecurrenceFrequency()), series.getRecurrenceInterval(),
                        weekdays.getOrDefault(series.getId(), Set.of()), series.getMonthDay(),
                        Boolean.TRUE.equals(series.getLastDayOfMonth()), series.getZoneId(),
                        series.getStartLocalDate(), series.getLocalTime(), series.getEndLocalDate());
                if (!rule.isNominalOccurrence(series.getNextNominalLocalTime())) {
                    // This cannot be repaired by a tzdb/policy revision. Abort this
                    // transaction, leave the target revision unpublished, and retry
                    // only after the persisted data is repaired.
                    throw new IllegalStateException("active work recurrence cursor does not match its rule");
                }
                WorkRecurrenceResolution resolution = rule.resolve(series.getNextNominalLocalTime());
                if (!Objects.equals(series.getNextDueAt(), resolution.resolvedUtcTime())) {
                    // A valid nominal cursor is retained, but its persisted UTC effect
                    // is rewritten under the exact target tzdb/policy before READY.
                    if (seriesMapper.updateActiveNextDueAt(series.getTenantId(), series.getId(),
                            resolution.resolvedUtcTime()) != 1) {
                        throw new IllegalStateException("active work recurrence cursor was not updated");
                    }
                    series.setNextDueAt(resolution.resolvedUtcTime());
                }
                if (!Objects.equals(series.getNextDueAt(), resolution.resolvedUtcTime())) {
                    throw new IllegalStateException("active work recurrence due time does not match its rule");
                }
            } catch (WorkRecurrenceTimeRangeExceededException exception) {
                if (seriesMapper.endForRecurrenceTimeRange(series.getTenantId(), series.getId()) != 1) {
                    throw new IllegalStateException("active work recurrence cursor was not ended");
                }
                endedTenantIds.add(series.getTenantId());
            }
        }
        endedTenantIds.forEach(tenantId -> {
            if (seriesMapper.countActiveForRecurrenceReconciliation(tenantId) == 0) {
                scheduleManager.synchronize(new TenantId(tenantId), AutomationKind.WORK_TASK_RECURRENCE, false);
            }
        });
        WorkTaskSeriesPO last = candidates.getLast();
        if (candidates.size() < RECONCILIATION_BATCH_SIZE) return publishReady(policy, now);
        if (controlMapper.updateCursor(last.getTenantId(), last.getId()) != 1) {
            throw new IllegalStateException("work recurrence reconciliation cursor was not updated");
        }
        return WorkRecurrenceReadiness.RECONCILING;
    }

    private WorkRecurrenceReadiness publishReady(WorkRecurrencePolicy policy, LocalDateTime now) {
        if (controlMapper.publishReady(policy.tzdbFingerprint(), policy.policyRevision(), now) != 1) {
            throw new IllegalStateException("work recurrence control was not published");
        }
        return WorkRecurrenceReadiness.READY;
    }

    private void startReconciliation(WorkRecurrenceControlPO control, WorkRecurrencePolicy policy) {
        if (controlMapper.startReconciliation(policy.tzdbFingerprint(), policy.policyRevision()) != 1) {
            throw new IllegalStateException("work recurrence reconciliation was not started");
        }
        control.setReconciliationStatus("RECONCILING");
        control.setTargetTzdbFingerprint(policy.tzdbFingerprint());
        control.setTargetPolicyRevision(policy.policyRevision());
        control.setCursorTenantId(null);
        control.setCursorSeriesId(null);
    }

    private static boolean isReady(WorkRecurrenceControlPO control) {
        return control != null && (control.getReconciliationStatus() == null
                || "READY".equals(control.getReconciliationStatus()));
    }

    private static boolean isReconciling(WorkRecurrenceControlPO control) {
        return control != null && "RECONCILING".equals(control.getReconciliationStatus());
    }

    private static boolean targetMatches(WorkRecurrenceControlPO control, WorkRecurrencePolicy policy) {
        return policy.policyRevision().equals(control.getTargetPolicyRevision())
                && policy.tzdbFingerprint().equals(control.getTargetTzdbFingerprint());
    }

    private static void requireReadyStatus(WorkRecurrenceControlPO control) {
        if (!isReady(control)) throw new IllegalStateException("stored work recurrence reconciliation state is invalid");
    }

    private static long revisionNumber(String revision) {
        if (revision == null || !revision.matches("B27A_V[1-9][0-9]{0,8}")) {
            throw new IllegalStateException("stored work recurrence revision is invalid");
        }
        return Long.parseLong(revision.substring("B27A_V".length()));
    }
}
