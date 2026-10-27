package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record EmbeddedPageVO(Long id, String objectType, String platform, String name,
                             String description, String urlTemplate, int sortOrder,
                             String status, Long createdByActorId, Long version,
                             LocalDateTime createdTime, LocalDateTime updatedTime) {
}
