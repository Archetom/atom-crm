package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.model.WorkTaskReminderOccurrence;
import io.github.archetom.crm.domain.model.WorkTaskReminderClaim;
import io.github.archetom.crm.domain.model.WorkTaskReminderOccurrenceStatus;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskReminderOccurrenceId;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/** Durable reminder occurrence ledger; it is intentionally separate from B26 automation runs. */
public interface WorkTaskReminderRepository {

    void synchronizeForTask(TenantId tenantId, WorkTask task, LocalDateTime now,
                            Duration retryDeadline);

    WorkTaskReminderClaim claimDue(
            UUID leaseOwner, LocalDateTime now, Duration leaseDuration, int maxAttempts);

    Optional<WorkTaskReminderOccurrence> findClaimedForUpdate(
            TenantId tenantId, WorkTaskReminderOccurrenceId occurrenceId,
            UUID leaseOwner, long leaseFence, LocalDateTime now);

    void markEmitted(
            TenantId tenantId, WorkTaskReminderOccurrenceId occurrenceId,
            UUID leaseOwner, long leaseFence, LocalDateTime now);

    void cancelClaim(
            TenantId tenantId, WorkTaskReminderOccurrenceId occurrenceId,
            UUID leaseOwner, long leaseFence, LocalDateTime now);

    WorkTaskReminderOccurrenceStatus recoverFailure(
            TenantId tenantId, WorkTaskReminderOccurrenceId occurrenceId,
            UUID leaseOwner, long leaseFence, LocalDateTime now,
            int maxAttempts, Duration retryBackoff);

    LocalDateTime currentUtc();

    void purgeTerminal(LocalDateTime beforeExclusive, int limit);
}
