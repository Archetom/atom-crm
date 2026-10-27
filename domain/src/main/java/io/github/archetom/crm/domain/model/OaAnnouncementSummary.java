package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;

import java.time.LocalDateTime;

/** Root-only management projection; audience selectors are intentionally absent. */
public record OaAnnouncementSummary(
        OaAnnouncementId id,
        MemberId createdByMemberId,
        String title,
        String body,
        OaAnnouncementStatus status,
        LocalDateTime scheduledPublishTime,
        MemberId scheduledByMemberId,
        OaAnnouncementPublicationSafeReason lastPublicationSafeReason,
        LocalDateTime publishedTime,
        LocalDateTime withdrawnTime,
        Long version,
        LocalDateTime createdTime,
        LocalDateTime updatedTime) {

    public OaAnnouncementSummary {
        if (id == null || createdByMemberId == null || title == null || body == null
                || status == null || version == null || version < 0
                || createdTime == null || updatedTime == null) {
            throw new IllegalArgumentException("OA announcement summary is invalid");
        }
    }
}
