package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class NotificationResponse {
    private Long id;
    private String kind;
    private Long taskId;
    private String title;
    private String priority;
    private LocalDateTime dueTime;
    private String status;
    private LocalDateTime readTime;
    private LocalDateTime createdTime;
}
