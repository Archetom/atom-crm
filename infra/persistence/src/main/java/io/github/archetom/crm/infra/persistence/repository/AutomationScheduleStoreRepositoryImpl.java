package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.application.automation.AutomationExecutionContext;
import io.github.archetom.crm.application.automation.AutomationClaimResult;
import io.github.archetom.crm.application.automation.AutomationKind;
import io.github.archetom.crm.application.automation.AutomationLeaseLostException;
import io.github.archetom.crm.application.automation.AutomationMaterialization;
import io.github.archetom.crm.application.automation.AutomationOutcome;
import io.github.archetom.crm.application.automation.AutomationPolicy;
import io.github.archetom.crm.application.automation.AutomationRevisionReconciliation;
import io.github.archetom.crm.application.automation.AutomationRunStateCount;
import io.github.archetom.crm.application.automation.AutomationRunStatus;
import io.github.archetom.crm.application.automation.AutomationRunTransition;
import io.github.archetom.crm.application.automation.AutomationScheduleStateCount;
import io.github.archetom.crm.application.automation.AutomationScheduleStatus;
import io.github.archetom.crm.application.automation.AutomationSchedulingReadiness;
import io.github.archetom.crm.application.automation.AutomationSchedulingSnapshot;
import io.github.archetom.crm.application.automation.WorkRecurrencePolicy;
import io.github.archetom.crm.application.port.out.automation.AutomationScheduleStore;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.properties.WorkRecurrenceProperties;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AutomationRunMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AutomationControlMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AutomationScheduleMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkRecurrenceControlMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.AutomationControlPO;
import io.github.archetom.crm.infra.persistence.mysql.po.AutomationRunPO;
import io.github.archetom.crm.infra.persistence.mysql.po.AutomationSchedulePO;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkRecurrenceControlPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** MySQL-owned schedule/run occurrence, lease, fence, and bounded-retention ledger. */
@Repository
@RequiredArgsConstructor
public class AutomationScheduleStoreRepositoryImpl implements AutomationScheduleStore {
    private static final int RETENTION_BATCH = 100;
    private static final int EXHAUSTED_CLAIM_CLEANUP_LIMIT = 20;

    private final AutomationControlMapper controlMapper;
    private final AutomationScheduleMapper scheduleMapper;
    private final AutomationRunMapper runMapper;
    private final WorkRecurrenceControlMapper recurrenceControlMapper;
    private final SchedulingProperties schedulingProperties;
    private final WorkRecurrenceProperties recurrenceProperties;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void preparePoolConfiguration(
            TenantId tenantId, AutomationKind kind, boolean createIfMissing, AutomationPolicy policy) {
        require(tenantId, kind, policy);
        String authoritativeRevision = ensureControlRevision(policy, utcNow());
        AutomationSchedulePO current = scheduleMapper.selectForUpdate(tenantId.getValue(), kind.name());
        if (current != null || !createIfMissing) return;
        scheduleMapper.insertPausedIfAbsent(tenantId.getValue(), kind.name(), authoritativeRevision);
        if (scheduleMapper.selectForUpdate(tenantId.getValue(), kind.name()) == null) {
            throw new IllegalStateException("automation schedule is unavailable");
        }
    }

    @Override
    public List<AutomationRunTransition> synchronize(
            TenantId tenantId, AutomationKind kind, boolean active, AutomationPolicy policy) {
        require(tenantId, kind, policy);
        LocalDateTime now = utcNow();
        String authoritativeRevision = ensureControlRevision(policy, now);
        AutomationSchedulePO current = scheduleMapper.selectForUpdate(tenantId.getValue(), kind.name());
        boolean created = false;
        if (current == null) {
            if (!active) return List.of();
            scheduleMapper.insertPausedIfAbsent(tenantId.getValue(), kind.name(), authoritativeRevision);
            current = scheduleMapper.selectForUpdate(tenantId.getValue(), kind.name());
            created = true;
        }
        if (current == null) throw new IllegalStateException("automation schedule is unavailable");
        boolean shouldActivate = active;
        boolean wasActive = AutomationScheduleStatus.ACTIVE.name().equals(current.getStatus());
        boolean revisionChanged = !authoritativeRevision.equals(current.getCadenceRevision());
        if (shouldActivate == wasActive && !revisionChanged) return List.of();

        // preparePoolConfiguration creates a new PAUSED row before its first pool-root write.
        // Its untouched version marks the first activation, which is generation 1 rather
        // than a false restart to generation 2.
        boolean firstPreparedActivation = shouldActivate && !wasActive && current.getVersion() == 0;
        long nextGeneration = (created || firstPreparedActivation)
                ? current.getGeneration() : current.getGeneration() + 1;
        if (scheduleMapper.updateState(tenantId.getValue(), kind.name(),
                shouldActivate ? AutomationScheduleStatus.ACTIVE.name() : AutomationScheduleStatus.PAUSED.name(),
                nextGeneration, authoritativeRevision, shouldActivate ? now : null) != 1) {
            throw new IllegalStateException("automation schedule state was not updated");
        }
        if (!created && !firstPreparedActivation) {
            int cancelled = runMapper.cancelActive(tenantId.getValue(), kind.name(), now);
            return cancelled == 0 ? List.of()
                    : List.of(new AutomationRunTransition(kind, io.github.archetom.crm.application.automation.AutomationRunMetric.CANCELLED,
                    cancelled));
        }
        return List.of();
    }

    @Override
    public List<AutomationRunTransition> synchronizeCurrentRun(
            AutomationExecutionContext context, boolean active, AutomationPolicy policy) {
        requireContext(context);
        requirePolicy(policy);
        AutomationSchedulePO current = scheduleMapper.selectForUpdate(
                context.tenantId().getValue(), context.kind().name());
        if (current == null || !AutomationScheduleStatus.ACTIVE.name().equals(current.getStatus())
                || current.getGeneration() != context.generation()) {
            throw new AutomationLeaseLostException();
        }
        if (active) return List.of();
        LocalDateTime now = utcNow();
        if (scheduleMapper.updateState(context.tenantId().getValue(), context.kind().name(),
                AutomationScheduleStatus.PAUSED.name(), current.getGeneration() + 1,
                current.getCadenceRevision(), null) != 1) {
            throw new AutomationLeaseLostException();
        }
        int cancelled = runMapper.cancelActiveExcept(context.tenantId().getValue(), context.kind().name(),
                context.runId(), now);
        return cancelled == 0 ? List.of()
                : List.of(new AutomationRunTransition(context.kind(),
                io.github.archetom.crm.application.automation.AutomationRunMetric.CANCELLED, cancelled));
    }

    @Override
    public AutomationRevisionReconciliation reconcileCadenceRevision(AutomationPolicy policy) {
        requirePolicy(policy);
        return requiresNew().execute(status -> {
            LocalDateTime now = utcNow();
            AutomationControlPO control = controlMapper.selectForUpdate();
            if (control == null) {
                controlMapper.insertIfAbsent(policy.cadenceRevision(), now);
                control = controlMapper.selectForUpdate();
            }
            if (control == null) throw new IllegalStateException("automation control is unavailable");
            long authoritativeNumber = revisionNumber(control.getCadenceRevision());
            if (policy.cadenceRevisionNumber() < authoritativeNumber) {
                return new AutomationRevisionReconciliation(AutomationSchedulingReadiness.REVISION_DRIFT, List.of());
            }
            if (policy.cadenceRevisionNumber() > authoritativeNumber
                    && controlMapper.updateRevision(policy.cadenceRevision(), now) != 1) {
                throw new IllegalStateException("automation control revision was not updated");
            }
            List<AutomationRunTransition> transitions = runMapper
                    .selectActiveCountsForRevisionDrift(policy.cadenceRevision()).stream()
                    .map(metric -> new AutomationRunTransition(AutomationKind.valueOf(metric.getKind()),
                            io.github.archetom.crm.application.automation.AutomationRunMetric.CANCELLED,
                            metric.getCount()))
                    .toList();
            if (scheduleMapper.reconcileCadenceRevision(policy.cadenceRevision(), now) > 0) {
                runMapper.cancelStaleScheduleRuns(policy.cadenceRevision(), now);
            }
            return new AutomationRevisionReconciliation(AutomationSchedulingReadiness.READY, transitions);
        });
    }

    @Override
    public AutomationSchedulingReadiness schedulingReadiness(AutomationPolicy policy) {
        requirePolicy(policy);
        AutomationControlPO control = controlMapper.select();
        if (control == null) return AutomationSchedulingReadiness.NOT_RECONCILED;
        if (!policy.cadenceRevision().equals(control.getCadenceRevision())) {
            return AutomationSchedulingReadiness.REVISION_DRIFT;
        }
        return scheduleMapper.countRevisionDrift(policy.cadenceRevision()) == 0
                ? AutomationSchedulingReadiness.READY : AutomationSchedulingReadiness.REVISION_DRIFT;
    }

    @Override
    public LocalDateTime currentUtc() {
        return utcNow();
    }

    @Override
    public Optional<AutomationMaterialization> materializeOne(AutomationPolicy policy,
                                                               java.util.Set<AutomationKind> eligibleKinds) {
        requirePolicy(policy);
        EnumSet<AutomationKind> requestedKinds = requestedEligibleKinds(eligibleKinds);
        if (requestedKinds.isEmpty()) return Optional.empty();
        return Optional.ofNullable(requiresNew().execute(status -> {
            // Keep the cross-control order aligned with recurrence reconciliation:
            // recurrence control -> B26 automation control -> schedule/run.
            Collection<String> kinds = admittedKindNames(requestedKinds);
            if (kinds.isEmpty()) return null;
            if (!lockAndRequireCurrentRevision(policy)) return null;
            LocalDateTime now = utcNow();
            AutomationSchedulePO schedule = scheduleMapper.selectDueForUpdate(now, policy.cadenceRevision(), kinds);
            if (schedule == null) return null;
            AutomationRunPO run = new AutomationRunPO();
            run.setTenantId(schedule.getTenantId());
            run.setKind(schedule.getKind());
            run.setGeneration(schedule.getGeneration());
            run.setDueAt(schedule.getNextDueAt());
            run.setEvaluationTime(now);
            run.setAvailableAt(now);
            run.setDeadlineAt(now.plus(policy.runDeadline()));
            try {
                if (runMapper.insert(run) != 1) {
                    throw new IllegalStateException("automation run was not materialized");
                }
            } catch (DuplicateKeyException exception) {
                return null;
            }
            // COALESCE_LATEST: one current run after downtime, never an interval backlog.
            AutomationKind kind = AutomationKind.valueOf(schedule.getKind());
            if (scheduleMapper.advanceDue(schedule.getTenantId(), schedule.getKind(),
                    schedule.getGeneration(), now.plus(policy.cadenceFor(kind))) != 1) {
                throw new AutomationLeaseLostException();
            }
            return new AutomationMaterialization(kind,
                    Duration.between(schedule.getNextDueAt(), now));
        }));
    }

    @Override
    public AutomationClaimResult claimOne(AutomationPolicy policy, UUID leaseOwner,
                                          java.util.Set<AutomationKind> eligibleKinds) {
        requirePolicy(policy);
        if (leaseOwner == null) throw new IllegalArgumentException("automation lease owner is required");
        EnumSet<AutomationKind> requestedKinds = requestedEligibleKinds(eligibleKinds);
        if (requestedKinds.isEmpty()) return AutomationClaimResult.empty();
        return requiresNew().execute(status -> {
            // See materializeOne: exact recurrence admission must acquire the
            // recurrence control before B26's automation control.
            Collection<String> kinds = admittedKindNames(requestedKinds);
            if (kinds.isEmpty()) return AutomationClaimResult.empty();
            if (!lockAndRequireCurrentRevision(policy)) return AutomationClaimResult.empty();
            LocalDateTime now = utcNow();
            List<AutomationRunTransition> transitions = new ArrayList<>();
            for (int skipped = 0; skipped < EXHAUSTED_CLAIM_CLEANUP_LIMIT; skipped++) {
                AutomationRunPO run = runMapper.selectClaimableForUpdate(
                        now, policy.cadenceRevision(), policy.maxAttempts(), kinds);
                if (run == null) return new AutomationClaimResult(Optional.empty(), transitions);
                if (run.getAttempt() >= policy.maxAttempts() || !run.getDeadlineAt().isAfter(now)) {
                    if (runMapper.deadBeforeClaim(run.getId(), now, policy.maxAttempts(), now) != 1) {
                        throw new AutomationLeaseLostException();
                    }
                    transitions.add(new AutomationRunTransition(AutomationKind.valueOf(run.getKind()),
                            io.github.archetom.crm.application.automation.AutomationRunMetric.DEAD, 1));
                    continue;
                }
                if (runMapper.lease(run.getId(), leaseOwner.toString(), now.plus(policy.leaseDuration()),
                        now, policy.maxAttempts()) != 1) {
                    throw new AutomationLeaseLostException();
                }
                run.setLeaseOwner(leaseOwner.toString());
                run.setLeaseFence(run.getLeaseFence() + 1);
                run.setAttempt(run.getAttempt() + 1);
                return new AutomationClaimResult(Optional.of(context(run, leaseOwner)), transitions);
            }
            return new AutomationClaimResult(Optional.empty(), transitions);
        });
    }

    @Override
    public LocalDateTime requireCurrent(AutomationExecutionContext context) {
        requireContext(context);
        LocalDateTime now = utcNow();
        AutomationSchedulePO schedule = scheduleMapper.selectForUpdate(
                context.tenantId().getValue(), context.kind().name());
        if (schedule == null || !AutomationScheduleStatus.ACTIVE.name().equals(schedule.getStatus())
                || schedule.getGeneration() != context.generation()) {
            throw new AutomationLeaseLostException();
        }
        AutomationRunPO run = runMapper.selectCurrentLeaseForUpdate(context.runId(),
                context.tenantId().getValue(), context.kind().name(), context.generation(),
                context.leaseOwner().toString(), context.leaseFence(), now);
        if (run == null) throw new AutomationLeaseLostException();
        return now;
    }

    @Override
    public AutomationRunTransition succeed(AutomationExecutionContext context, AutomationOutcome outcome) {
        requireContext(context);
        if (outcome == null) throw new IllegalArgumentException("automation outcome is required");
        if (runMapper.succeed(context.runId(), context.tenantId().getValue(), context.kind().name(),
                context.generation(), context.leaseOwner().toString(), context.leaseFence(),
                outcome.resultCode(), outcome.affectedCount(), utcNow()) != 1) {
            throw new AutomationLeaseLostException();
        }
        return new AutomationRunTransition(context.kind(),
                io.github.archetom.crm.application.automation.AutomationRunMetric.SUCCEEDED, 1);
    }

    @Override
    public AutomationRunTransition recover(AutomationExecutionContext context, AutomationPolicy policy) {
        requireContext(context);
        requirePolicy(policy);
        return requiresNew().execute(status -> {
            AutomationRunPO run = runMapper.selectByIdForUpdate(context.runId());
            if (run == null || !AutomationRunStatus.LEASED.name().equals(run.getStatus())
                    || !context.leaseOwner().toString().equals(run.getLeaseOwner())
                    || run.getLeaseFence() != context.leaseFence()) {
                throw new AutomationLeaseLostException();
            }
            LocalDateTime now = utcNow();
            boolean exhausted = run.getAttempt() >= policy.maxAttempts()
                    || !run.getDeadlineAt().isAfter(now);
            int updated = runMapper.recover(run.getId(), context.leaseOwner().toString(), context.leaseFence(),
                    exhausted ? AutomationRunStatus.DEAD.name() : AutomationRunStatus.RETRY_WAIT.name(),
                    exhausted ? now : now.plus(policy.retryBackoff()),
                    exhausted ? "RETRY_EXHAUSTED" : "RETRY", exhausted ? now : null, now);
            if (updated != 1) throw new AutomationLeaseLostException();
            return new AutomationRunTransition(context.kind(), exhausted
                    ? io.github.archetom.crm.application.automation.AutomationRunMetric.DEAD
                    : io.github.archetom.crm.application.automation.AutomationRunMetric.RETRY, 1);
        });
    }

    @Override
    public AutomationSchedulingSnapshot schedulingSnapshot() {
        List<AutomationRunStateCount> runStates = runMapper.selectStateCounts().stream()
                .map(metric -> new AutomationRunStateCount(AutomationKind.valueOf(metric.getKind()),
                        AutomationRunStatus.valueOf(metric.getStatus()), metric.getCount()))
                .toList();
        List<AutomationScheduleStateCount> scheduleStates = scheduleMapper.selectStateCounts().stream()
                .map(metric -> new AutomationScheduleStateCount(AutomationKind.valueOf(metric.getKind()),
                        AutomationScheduleStatus.valueOf(metric.getStatus()), metric.getCount()))
                .toList();
        Map<AutomationKind, LocalDateTime> latestSuccess = new EnumMap<>(AutomationKind.class);
        runMapper.selectLatestSuccessUtc().forEach(metric -> latestSuccess.put(
                AutomationKind.valueOf(metric.getKind()), metric.getLatestSuccessUtc()));
        return new AutomationSchedulingSnapshot(runStates, scheduleStates, latestSuccess);
    }

    @Override
    public int purgeTerminal(AutomationPolicy policy) {
        requirePolicy(policy);
        Integer removed = requiresNew().execute(status -> {
            LocalDateTime now = utcNow();
            return runMapper.deleteTerminalBefore(now.minus(policy.terminalRetention()), RETENTION_BATCH);
        });
        return removed == null ? 0 : removed;
    }

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private LocalDateTime utcNow() {
        LocalDateTime now = scheduleMapper.selectUtcNow();
        if (now == null) throw new IllegalStateException("automation database UTC is unavailable");
        return now;
    }

    private String ensureControlRevision(AutomationPolicy policy, LocalDateTime now) {
        AutomationControlPO control = controlMapper.selectForUpdate();
        if (control == null) {
            controlMapper.insertIfAbsent(policy.cadenceRevision(), now);
            control = controlMapper.selectForUpdate();
        }
        if (control == null) throw new IllegalStateException("automation control is unavailable");
        return control.getCadenceRevision();
    }

    private boolean lockAndRequireCurrentRevision(AutomationPolicy policy) {
        AutomationControlPO control = controlMapper.selectForUpdate();
        return control != null && policy.cadenceRevision().equals(control.getCadenceRevision())
                && scheduleMapper.countRevisionDrift(policy.cadenceRevision()) == 0;
    }

    private static long revisionNumber(String revision) {
        if (revision == null || !revision.matches("B26A_V[1-9][0-9]{0,8}")) {
            throw new IllegalStateException("automation control revision is invalid");
        }
        return Long.parseLong(revision.substring("B26A_V".length()));
    }

    private static AutomationExecutionContext context(AutomationRunPO run, UUID owner) {
        return new AutomationExecutionContext(run.getId(), new TenantId(run.getTenantId()),
                AutomationKind.valueOf(run.getKind()), run.getGeneration(), owner, run.getLeaseFence(),
                run.getAttempt(), run.getDueAt(), run.getEvaluationTime());
    }

    private static void require(TenantId tenantId, AutomationKind kind, AutomationPolicy policy) {
        if (tenantId == null || kind == null) throw new IllegalArgumentException("automation schedule key is invalid");
        requirePolicy(policy);
    }

    private static void requireContext(AutomationExecutionContext context) {
        if (context == null) throw new IllegalArgumentException("automation execution context is required");
    }

    private static void requirePolicy(AutomationPolicy policy) {
        if (policy == null) throw new IllegalArgumentException("automation policy is required");
    }

    /**
     * Rechecks recurrence readiness under the same control-row lock as selecting a
     * schedule or run. A stale replica's earlier handler snapshot therefore cannot
     * materialize or lease recurrence work once another replica starts RECONCILING.
     */
    private Collection<String> admittedKindNames(EnumSet<AutomationKind> requestedKinds) {
        EnumSet<AutomationKind> admitted = EnumSet.copyOf(requestedKinds);
        if (admitted.contains(AutomationKind.WORK_TASK_RECURRENCE) && !recurrenceReadyForNewRuns()) {
            admitted.remove(AutomationKind.WORK_TASK_RECURRENCE);
        }
        return admitted.stream().map(Enum::name).toList();
    }

    private boolean recurrenceReadyForNewRuns() {
        if (!schedulingProperties.recurrenceValid()) return false;
        WorkRecurrencePolicy policy = recurrenceProperties.policy();
        if (!policy.enabled() || !policy.valid()) return false;
        WorkRecurrenceControlPO control = recurrenceControlMapper.selectForUpdate();
        return control != null && (control.getReconciliationStatus() == null
                || "READY".equals(control.getReconciliationStatus()))
                && policy.policyRevision().equals(control.getPolicyRevision())
                && policy.tzdbFingerprint().equals(control.getTzdbFingerprint());
    }

    private static EnumSet<AutomationKind> requestedEligibleKinds(java.util.Set<AutomationKind> eligibleKinds) {
        if (eligibleKinds == null || eligibleKinds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("eligible automation kinds are invalid");
        }
        if (eligibleKinds.isEmpty()) return EnumSet.noneOf(AutomationKind.class);
        return EnumSet.copyOf(eligibleKinds);
    }
}
