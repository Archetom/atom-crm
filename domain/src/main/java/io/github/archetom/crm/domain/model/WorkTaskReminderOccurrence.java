package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import io.github.archetom.crm.domain.valueobject.WorkTaskReminderOccurrenceId;

import java.time.LocalDateTime;
import java.util.UUID;

/** Durable exact reminder effect identity. Lease data never becomes a public API value. */
public record WorkTaskReminderOccurrence(
        WorkTaskReminderOccurrenceId id,
        TenantId tenantId,
        WorkTaskId taskId,
        long notificationProjectionVersion,
        String stepKey,
        LocalDateTime dueTime,
        WorkTaskReminderOccurrenceStatus status,
        int attemptCount,
        LocalDateTime availableAt,
        LocalDateTime deadlineAt,
        UUID leaseOwner,
        long leaseFence,
        LocalDateTime leaseUntil,
        LocalDateTime createdTime,
        LocalDateTime terminalTime,
        String safeReason) {

    public WorkTaskReminderOccurrence {
        if (id == null || tenantId == null || taskId == null || notificationProjectionVersion < 0
                || stepKey == null || !stepKey.matches("[A-Z0-9_]{3,128}") || dueTime == null
                || status == null || attemptCount < 0 || availableAt == null || deadlineAt == null
                || !deadlineAt.isAfter(dueTime) || leaseFence < 0 || createdTime == null) {
            throw new IllegalArgumentException("work task reminder occurrence is invalid");
        }
    }

    public boolean isClaimedBy(UUID owner, long fence) {
        return status == WorkTaskReminderOccurrenceStatus.LEASED
                && leaseOwner != null && leaseOwner.equals(owner) && leaseFence == fence;
    }
}
