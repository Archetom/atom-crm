package io.github.archetom.crm.application.oa;

import io.github.archetom.crm.domain.model.OaAnnouncementPublicationSafeReason;
import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.LocalDateTime;
import java.util.UUID;

/** Application-owned exact one-shot work fact; it is not an automation run. */
public record OaAnnouncementPublicationOccurrence(
        Long id,
        TenantId tenantId,
        OaAnnouncementId announcementId,
        long publicationGeneration,
        LocalDateTime dueTime,
        OaAnnouncementPublicationOccurrenceStatus status,
        int attemptCount,
        LocalDateTime availableAt,
        LocalDateTime deadlineAt,
        UUID leaseOwner,
        long leaseFence,
        LocalDateTime leaseUntil,
        LocalDateTime createdTime,
        LocalDateTime terminalTime,
        OaAnnouncementPublicationSafeReason safeReason) {

    public OaAnnouncementPublicationOccurrence {
        if (id == null || id < 1 || tenantId == null || announcementId == null
                || publicationGeneration < 1 || dueTime == null || status == null
                || attemptCount < 0 || availableAt == null || deadlineAt == null
                || !deadlineAt.isAfter(dueTime) || leaseFence < 0 || createdTime == null) {
            throw new IllegalArgumentException("OA announcement publication occurrence is invalid");
        }
    }
}
