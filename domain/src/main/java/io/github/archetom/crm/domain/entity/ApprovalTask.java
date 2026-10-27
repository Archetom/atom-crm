package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ApprovalDomainException;
import io.github.archetom.crm.domain.model.ApprovalStepMode;
import io.github.archetom.crm.domain.model.ApprovalTaskStatus;
import io.github.archetom.crm.domain.valueobject.MemberId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** One immutable assignment identity with versioned action state. */
@Getter
public class ApprovalTask {

    private String taskKey;
    private String sourceTaskKey;
    private String stepCode;
    private String stepName;
    private int stepOrder;
    private ApprovalStepMode mode;
    private int actorOrder;
    private MemberId assignedMemberId;
    private ApprovalTaskStatus status;
    private String remark;
    private LocalDateTime actedTime;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private boolean pendingPersistenceChanges;

    private ApprovalTask() {
    }

    public record Snapshot(
            String taskKey,
            String sourceTaskKey,
            String stepCode,
            String stepName,
            int stepOrder,
            ApprovalStepMode mode,
            int actorOrder,
            MemberId assignedMemberId,
            ApprovalTaskStatus status,
            String remark,
            LocalDateTime actedTime,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static ApprovalTask create(
            String stepCode,
            String stepName,
            int stepOrder,
            ApprovalStepMode mode,
            int actorOrder,
            MemberId assignedMemberId,
            ApprovalTaskStatus status) {
        return build(UUID.randomUUID().toString(), null, stepCode, stepName,
                stepOrder, mode, actorOrder, assignedMemberId, status,
                null, null, 0L, null, null);
    }

    public static ApprovalTask replacement(ApprovalTask source, MemberId target) {
        if (source == null || source.status != ApprovalTaskStatus.TRANSFERRED) {
            throw new ApprovalDomainException("transferred approval task is required");
        }
        return build(UUID.randomUUID().toString(), source.taskKey,
                source.stepCode, source.stepName, source.stepOrder, source.mode,
                source.actorOrder, target, ApprovalTaskStatus.PENDING,
                null, null, 0L, null, null);
    }

    public static ApprovalTask reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new ApprovalDomainException("approval task snapshot must not be empty");
        }
        return build(snapshot.taskKey(), snapshot.sourceTaskKey(), snapshot.stepCode(),
                snapshot.stepName(), snapshot.stepOrder(), snapshot.mode(),
                snapshot.actorOrder(), snapshot.assignedMemberId(), snapshot.status(),
                snapshot.remark(), snapshot.actedTime(), snapshot.version(),
                snapshot.createdTime(), snapshot.updatedTime());
    }

    public void activate() {
        requireStatus(ApprovalTaskStatus.WAITING);
        status = ApprovalTaskStatus.PENDING;
        touch();
    }

    public void approve(String actionRemark) {
        decide(ApprovalTaskStatus.APPROVED, actionRemark);
    }

    public void reject(String actionRemark) {
        decide(ApprovalTaskStatus.REJECTED, actionRemark);
    }

    public void transfer(String actionRemark) {
        decide(ApprovalTaskStatus.TRANSFERRED, actionRemark);
    }

    public void skip() {
        if (status != ApprovalTaskStatus.WAITING && status != ApprovalTaskStatus.PENDING) {
            throw new ApprovalDomainException("only open approval tasks can be skipped");
        }
        status = ApprovalTaskStatus.SKIPPED;
        actedTime = LocalDateTime.now();
        touch();
    }

    public boolean isOpen() {
        return status == ApprovalTaskStatus.WAITING || status == ApprovalTaskStatus.PENDING;
    }

    public boolean isPendingFor(MemberId memberId) {
        return status == ApprovalTaskStatus.PENDING && assignedMemberId.equals(memberId);
    }

    public boolean hasPendingPersistenceChanges() {
        return createdTime == null || pendingPersistenceChanges;
    }

    public void onPersisted(Long persistedVersion,
                            LocalDateTime created, LocalDateTime updated) {
        version = persistedVersion == null ? 0L : persistedVersion;
        if (created != null) {
            createdTime = created;
        }
        if (updated != null) {
            updatedTime = updated;
        }
        pendingPersistenceChanges = false;
    }

    private void decide(ApprovalTaskStatus next, String actionRemark) {
        requireStatus(ApprovalTaskStatus.PENDING);
        status = next;
        remark = optional(actionRemark, 1000, "approval task remark");
        actedTime = LocalDateTime.now();
        touch();
    }

    private void requireStatus(ApprovalTaskStatus expected) {
        if (status != expected) {
            throw new ApprovalDomainException("approval task is not actionable");
        }
    }

    private void touch() {
        updatedTime = LocalDateTime.now();
        pendingPersistenceChanges = true;
    }

    private static ApprovalTask build(
            String taskKey,
            String sourceTaskKey,
            String stepCode,
            String stepName,
            int stepOrder,
            ApprovalStepMode mode,
            int actorOrder,
            MemberId assignedMemberId,
            ApprovalTaskStatus status,
            String remark,
            LocalDateTime actedTime,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
        if (taskKey == null || !taskKey.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || sourceTaskKey != null && !sourceTaskKey.matches("[0-9a-f-]{36}")
                || stepCode == null || stepCode.isBlank() || stepCode.length() > 64
                || stepName == null || stepName.isBlank() || stepName.length() > 100
                || stepOrder < 0 || actorOrder < 0 || mode == null
                || assignedMemberId == null || status == null) {
            throw new ApprovalDomainException("approval task is invalid");
        }
        ApprovalTask task = new ApprovalTask();
        task.taskKey = taskKey;
        task.sourceTaskKey = sourceTaskKey;
        task.stepCode = stepCode;
        task.stepName = stepName;
        task.stepOrder = stepOrder;
        task.mode = mode;
        task.actorOrder = actorOrder;
        task.assignedMemberId = assignedMemberId;
        task.status = status;
        task.remark = optional(remark, 1000, "approval task remark");
        task.actedTime = actedTime;
        task.version = version == null ? 0L : version;
        task.createdTime = createdTime;
        task.updatedTime = updatedTime;
        task.pendingPersistenceChanges = createdTime == null;
        return task;
    }

    private static String optional(String value, int maximum, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximum) {
            throw new ApprovalDomainException(description + " is too long");
        }
        return normalized;
    }
}
