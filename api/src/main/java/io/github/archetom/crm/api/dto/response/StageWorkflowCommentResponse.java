package io.github.archetom.crm.api.dto.response;

import java.time.LocalDateTime;

public record StageWorkflowCommentResponse(Long id, Long workflowRecordId,
                                           String content, boolean canDelete, Long version,
                                           LocalDateTime createdTime) {
}
