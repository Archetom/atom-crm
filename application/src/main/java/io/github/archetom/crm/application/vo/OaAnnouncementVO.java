package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;

public record OaAnnouncementVO(
        Long id,
        Long createdByActorId,
        String title,
        String body,
        List<OaAnnouncementAudienceSelectorVO> audience,
        String status,
        Long recipientTotal,
        Long readTotal,
        LocalDateTime scheduledPublishTime,
        Long scheduledByActorId,
        String lastPublicationSafeReason,
        LocalDateTime publishedTime,
        LocalDateTime withdrawnTime,
        List<String> allowedActions,
        Long version,
        LocalDateTime createdTime,
        LocalDateTime updatedTime) {
}
