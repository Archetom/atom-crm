package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record OaAnnouncementReadReceiptVO(
        Long announcementId,
        LocalDateTime readTime) {
}
