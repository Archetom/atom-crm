package io.github.archetom.crm.application.port.out.oa;

import io.github.archetom.crm.application.oa.OaAnnouncementPublicationClaim;
import io.github.archetom.crm.application.oa.OaAnnouncementPublicationOccurrence;
import io.github.archetom.crm.domain.model.OaAnnouncementPublicationSafeReason;
import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/** Durable, fenced OA-owned publication ledger. */
public interface OaAnnouncementPublicationStore {

    void create(TenantId tenantId, OaAnnouncementId announcementId,
                long publicationGeneration, LocalDateTime dueTime, LocalDateTime deadlineAt,
                LocalDateTime createdTime);

    /** @return true only when an active occurrence was transitioned to CANCELLED. */
    boolean cancelGeneration(TenantId tenantId, OaAnnouncementId announcementId,
                             long publicationGeneration, LocalDateTime now,
                             OaAnnouncementPublicationSafeReason reason);

    OaAnnouncementPublicationClaim claimDue(
            UUID leaseOwner, LocalDateTime now, Duration leaseDuration, int maxAttempts);

    Optional<OaAnnouncementPublicationOccurrence> findClaimedForUpdate(
            TenantId tenantId, Long occurrenceId, UUID leaseOwner,
            long leaseFence, LocalDateTime now);

    void markPublished(TenantId tenantId, Long occurrenceId, UUID leaseOwner,
                       long leaseFence, LocalDateTime now);

    void markCancelled(TenantId tenantId, Long occurrenceId, UUID leaseOwner,
                       long leaseFence, LocalDateTime now,
                       OaAnnouncementPublicationSafeReason reason);

    void markDead(TenantId tenantId, Long occurrenceId, UUID leaseOwner,
                  long leaseFence, LocalDateTime now,
                  OaAnnouncementPublicationSafeReason reason);

    void retry(TenantId tenantId, Long occurrenceId, UUID leaseOwner,
               long leaseFence, LocalDateTime now, Duration retryBackoff);

    LocalDateTime currentUtc();

    void purgeTerminal(LocalDateTime beforeExclusive, int limit);
}
