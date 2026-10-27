package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record NotificationPreferenceVO(boolean emailEnabled, boolean oaAnnouncementEmailEnabled,
                                       LocalDateTime updatedTime) {
}
