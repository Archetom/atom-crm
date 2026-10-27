package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record StageWorkflowCommentVO(
        Long id,
        Long workflowRecordId,
        String content,
        boolean canDelete,
        Long version,
        LocalDateTime createdTime) {
}
