package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;

import java.time.LocalDateTime;

public record OaAnnouncementInboxItem(
        OaAnnouncementId id,
        String title,
        String body,
        LocalDateTime publishedTime,
        LocalDateTime readTime) {

    public OaAnnouncementInboxItem {
        if (id == null || title == null || body == null || publishedTime == null) {
            throw new IllegalArgumentException("OA announcement inbox item is invalid");
        }
    }

    public boolean read() {
        return readTime != null;
    }
}
