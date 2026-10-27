package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.model.WorkTaskReminderOccurrence;
import io.github.archetom.crm.domain.model.WorkTaskReminderClaim;
import io.github.archetom.crm.domain.model.WorkTaskReminderOccurrenceStatus;
import io.github.archetom.crm.domain.repository.WorkTaskReminderRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import io.github.archetom.crm.domain.valueobject.WorkTaskReminderOccurrenceId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkTaskReminderOccurrenceMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskReminderOccurrencePO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/** MySQL occurrence identity, lease and task-local reconciliation adapter. */
@Repository
@RequiredArgsConstructor
public class WorkTaskReminderRepositoryImpl implements WorkTaskReminderRepository {
    private static final int CLAIM_CLEANUP_LIMIT = 10;
    private final WorkTaskReminderOccurrenceMapper mapper;

    @Override
    public void synchronizeForTask(TenantId tenantId, WorkTask task, LocalDateTime now,
                                   Duration retryDeadline) {
        require(tenantId, task);
        if (now == null || retryDeadline == null || retryDeadline.isNegative() || retryDeadline.isZero()) {
            throw new IllegalArgumentException("work task reminder scheduling policy is invalid");
        }
        task.getReminderPlan().validateForPersistence(task.getDueTime(), retryDeadline);
        boolean canSchedule = task.getStatus().isOpen() && task.getDueTime() != null
                && !task.getReminderPlan().isEmpty();
        LambdaUpdateWrapper<WorkTaskReminderOccurrencePO> cancel =
                new LambdaUpdateWrapper<WorkTaskReminderOccurrencePO>()
                        .eq(WorkTaskReminderOccurrencePO::getTenantId, tenantId.getValue())
                        .eq(WorkTaskReminderOccurrencePO::getWorkTaskId, task.getId().getValue())
                        .in(WorkTaskReminderOccurrencePO::getStatus,
                                WorkTaskReminderOccurrenceStatus.READY.name(),
                                WorkTaskReminderOccurrenceStatus.RETRY_WAIT.name(),
                                WorkTaskReminderOccurrenceStatus.LEASED.name())
                        .set(WorkTaskReminderOccurrencePO::getStatus,
                                WorkTaskReminderOccurrenceStatus.CANCELLED.name())
                        .set(WorkTaskReminderOccurrencePO::getLeaseOwner, null)
                        .set(WorkTaskReminderOccurrencePO::getLeaseUntil, null)
                        .set(WorkTaskReminderOccurrencePO::getTerminalTime, now);
        if (canSchedule) {
            cancel.ne(WorkTaskReminderOccurrencePO::getNotificationProjectionVersion,
                    task.getNotificationProjectionVersion());
        }
        mapper.update(null, cancel);
        if (!canSchedule) return;
        for (var step : task.getReminderPlan().steps()) {
            long existing = mapper.selectCount(new LambdaQueryWrapper<WorkTaskReminderOccurrencePO>()
                    .eq(WorkTaskReminderOccurrencePO::getTenantId, tenantId.getValue())
                    .eq(WorkTaskReminderOccurrencePO::getWorkTaskId, task.getId().getValue())
                    .eq(WorkTaskReminderOccurrencePO::getNotificationProjectionVersion,
                            task.getNotificationProjectionVersion())
                    .eq(WorkTaskReminderOccurrencePO::getStepKey, step.stepKey()));
            if (existing > 0) continue;
            WorkTaskReminderOccurrencePO row = new WorkTaskReminderOccurrencePO();
            row.setTenantId(tenantId.getValue());
            row.setWorkTaskId(task.getId().getValue());
            row.setNotificationProjectionVersion(task.getNotificationProjectionVersion());
            row.setStepKey(step.stepKey());
            row.setDueTime(step.dueAt(task.getDueTime()));
            row.setStatus(WorkTaskReminderOccurrenceStatus.READY.name());
            row.setAttemptCount(0);
            row.setAvailableAt(row.getDueTime());
            row.setDeadlineAt(row.getDueTime().plus(retryDeadline));
            row.setLeaseFence(0L);
            row.setCreatedTime(now);
            try {
                if (mapper.insert(row) != 1) {
                    throw new IllegalStateException("work task reminder occurrence insert failed");
                }
            } catch (DuplicateKeyException ignored) {
                // The task root lock normally serializes this path. The exact unique key
                // remains authoritative for recovery after an ambiguous commit outcome.
            }
        }
    }

    @Override
    public WorkTaskReminderClaim claimDue(
            UUID leaseOwner, LocalDateTime now, Duration leaseDuration, int maxAttempts) {
        if (leaseOwner == null || now == null || leaseDuration == null || leaseDuration.isNegative()
                || leaseDuration.isZero() || maxAttempts < 1 || maxAttempts > 20) {
            throw new IllegalArgumentException("work task reminder claim is invalid");
        }
        int deadCount = 0;
        for (int skipped = 0; skipped < CLAIM_CLEANUP_LIMIT; skipped++) {
            WorkTaskReminderOccurrencePO candidate = mapper.selectClaimableForUpdate(now, maxAttempts);
            if (candidate == null) return new WorkTaskReminderClaim(Optional.empty(), deadCount);
            if (candidate.getAttemptCount() >= maxAttempts || !candidate.getDeadlineAt().isAfter(now)) {
                if (mapper.deadBeforeClaim(candidate.getId(), now, maxAttempts) != 1) {
                    throw new IllegalStateException("work task reminder exhaustion transition was lost");
                }
                deadCount++;
                continue;
            }
            if (WorkTaskReminderOccurrenceStatus.LEASED.name().equals(candidate.getStatus())) {
                if (mapper.requeueExpiredLease(candidate.getId(), now) != 1) {
                    throw new IllegalStateException("work task reminder expired lease transition was lost");
                }
                continue;
            }
            // A lease may never outlive the retry deadline: otherwise an owner that
            // starts just before the cutoff could emit after the hard deadline.
            LocalDateTime until = now.plus(leaseDuration);
            if (until.isAfter(candidate.getDeadlineAt())) until = candidate.getDeadlineAt();
            if (mapper.claimReady(candidate.getId(), leaseOwner.toString(), until, now, maxAttempts) != 1) {
                throw new IllegalStateException("work task reminder claim was lost");
            }
            return new WorkTaskReminderClaim(Optional.ofNullable(mapper.selectById(candidate.getId())).map(this::domain),
                    deadCount);
        }
        return new WorkTaskReminderClaim(Optional.empty(), deadCount);
    }

    @Override
    public Optional<WorkTaskReminderOccurrence> findClaimedForUpdate(
            TenantId tenantId, WorkTaskReminderOccurrenceId occurrenceId,
            UUID leaseOwner, long leaseFence, LocalDateTime now) {
        if (tenantId == null || occurrenceId == null || leaseOwner == null || leaseFence < 1 || now == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<WorkTaskReminderOccurrencePO>()
                .eq(WorkTaskReminderOccurrencePO::getTenantId, tenantId.getValue())
                .eq(WorkTaskReminderOccurrencePO::getId, occurrenceId.getValue())
                .eq(WorkTaskReminderOccurrencePO::getStatus, WorkTaskReminderOccurrenceStatus.LEASED.name())
                .eq(WorkTaskReminderOccurrencePO::getLeaseOwner, leaseOwner.toString())
                .eq(WorkTaskReminderOccurrencePO::getLeaseFence, leaseFence)
                .ge(WorkTaskReminderOccurrencePO::getLeaseUntil, now)
                .gt(WorkTaskReminderOccurrencePO::getDeadlineAt, now)
                .last("FOR UPDATE"))).map(this::domain);
    }

    @Override
    public void markEmitted(TenantId tenantId, WorkTaskReminderOccurrenceId occurrenceId,
                            UUID leaseOwner, long leaseFence, LocalDateTime now) {
        transitionTerminal(tenantId, occurrenceId, leaseOwner, leaseFence,
                WorkTaskReminderOccurrenceStatus.EMITTED, now);
    }

    @Override
    public void cancelClaim(TenantId tenantId, WorkTaskReminderOccurrenceId occurrenceId,
                            UUID leaseOwner, long leaseFence, LocalDateTime now) {
        transitionTerminal(tenantId, occurrenceId, leaseOwner, leaseFence,
                WorkTaskReminderOccurrenceStatus.CANCELLED, now);
    }

    @Override
    public WorkTaskReminderOccurrenceStatus recoverFailure(TenantId tenantId, WorkTaskReminderOccurrenceId occurrenceId,
                                                           UUID leaseOwner, long leaseFence, LocalDateTime now,
                                                           int maxAttempts, Duration retryBackoff) {
        if (tenantId == null || occurrenceId == null || leaseOwner == null || leaseFence < 1 || now == null
                || maxAttempts < 1 || maxAttempts > 20 || retryBackoff == null
                || retryBackoff.isNegative() || retryBackoff.isZero()) {
            throw new IllegalArgumentException("work task reminder recovery is invalid");
        }
        if (mapper.recoverFailure(tenantId.getValue(), occurrenceId.getValue(), leaseOwner.toString(), leaseFence,
                now, maxAttempts, now.plus(retryBackoff)) != 1) {
            throw new IllegalStateException("work task reminder lease was lost during recovery");
        }
        WorkTaskReminderOccurrencePO recovered = mapper.selectById(occurrenceId.getValue());
        if (recovered == null) throw new IllegalStateException("work task reminder recovery disappeared");
        return WorkTaskReminderOccurrenceStatus.valueOf(recovered.getStatus());
    }

    @Override
    public LocalDateTime currentUtc() {
        LocalDateTime value = mapper.currentUtc();
        if (value == null) throw new IllegalStateException("work task reminder database time is unavailable");
        return value;
    }

    @Override
    public void purgeTerminal(LocalDateTime beforeExclusive, int limit) {
        if (beforeExclusive == null || limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("work task reminder retention request is invalid");
        }
        mapper.purgeTerminal(beforeExclusive, limit);
    }

    private void transitionTerminal(TenantId tenantId, WorkTaskReminderOccurrenceId occurrenceId,
                                    UUID leaseOwner, long leaseFence,
                                    WorkTaskReminderOccurrenceStatus target, LocalDateTime now) {
        if (tenantId == null || occurrenceId == null || leaseOwner == null || leaseFence < 1 || now == null) {
            throw new IllegalArgumentException("work task reminder transition is invalid");
        }
        int changed = mapper.update(null, new LambdaUpdateWrapper<WorkTaskReminderOccurrencePO>()
                .eq(WorkTaskReminderOccurrencePO::getTenantId, tenantId.getValue())
                .eq(WorkTaskReminderOccurrencePO::getId, occurrenceId.getValue())
                .eq(WorkTaskReminderOccurrencePO::getStatus, WorkTaskReminderOccurrenceStatus.LEASED.name())
                .eq(WorkTaskReminderOccurrencePO::getLeaseOwner, leaseOwner.toString())
                .eq(WorkTaskReminderOccurrencePO::getLeaseFence, leaseFence)
                // This statement is the final effect fence. Use the database UTC
                // instant, rather than the earlier admission sample, so a long
                // authorization path cannot emit after its lease or deadline.
                .apply("lease_until > UTC_TIMESTAMP(6)")
                .apply(target == WorkTaskReminderOccurrenceStatus.EMITTED,
                        "deadline_at > UTC_TIMESTAMP(6)")
                .set(WorkTaskReminderOccurrencePO::getStatus, target.name())
                .set(WorkTaskReminderOccurrencePO::getLeaseOwner, null)
                .set(WorkTaskReminderOccurrencePO::getLeaseUntil, null)
                .set(WorkTaskReminderOccurrencePO::getTerminalTime, now));
        if (changed != 1) throw new IllegalStateException("work task reminder lease was lost");
    }

    private WorkTaskReminderOccurrence domain(WorkTaskReminderOccurrencePO row) {
        return new WorkTaskReminderOccurrence(new WorkTaskReminderOccurrenceId(row.getId()),
                new TenantId(row.getTenantId()), new WorkTaskId(row.getWorkTaskId()),
                row.getNotificationProjectionVersion(), row.getStepKey(), row.getDueTime(),
                WorkTaskReminderOccurrenceStatus.valueOf(row.getStatus()), row.getAttemptCount(),
                row.getAvailableAt(), row.getDeadlineAt(),
                row.getLeaseOwner() == null ? null : UUID.fromString(row.getLeaseOwner()),
                row.getLeaseFence(), row.getLeaseUntil(), row.getCreatedTime(), row.getTerminalTime(),
                row.getSafeReason());
    }

    private static void require(TenantId tenantId, WorkTask task) {
        if (tenantId == null || task == null || task.getId() == null || task.getTenantId() == null
                || !tenantId.sameValueAs(task.getTenantId())) {
            throw new IllegalArgumentException("work task reminder tenant is invalid");
        }
    }
}
