package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record OaAnnouncementSummaryVO(
        Long id,
        Long createdByActorId,
        String title,
        String body,
        String status,
        Long recipientTotal,
        Long readTotal,
        LocalDateTime scheduledPublishTime,
        Long scheduledByActorId,
        String lastPublicationSafeReason,
        LocalDateTime publishedTime,
        LocalDateTime withdrawnTime,
        Long version,
        LocalDateTime createdTime,
        LocalDateTime updatedTime) {
}
