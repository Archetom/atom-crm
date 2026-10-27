package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record WorkTaskCommentVO(
        Long id,
        Long taskId,
        Long authorActorId,
        String content,
        Long version,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}
