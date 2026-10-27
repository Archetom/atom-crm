package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record OaAnnouncementInboxVO(
        Long id,
        String title,
        String body,
        boolean read,
        LocalDateTime readTime,
        LocalDateTime publishedTime) {
}
