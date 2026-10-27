package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record NotificationVO(
        Long id, String kind, Long taskId, String title, String priority, LocalDateTime dueTime,
        String status, LocalDateTime readTime, LocalDateTime createdTime) {
}
