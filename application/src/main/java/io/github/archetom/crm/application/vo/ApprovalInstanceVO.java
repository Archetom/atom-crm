package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ApprovalInstanceVO(
        Long id,
        Long definitionId,
        Long definitionVersion,
        String objectType,
        Long subjectId,
        String idempotencyKey,
        String title,
        String status,
        Integer currentStepOrder,
        Map<String, List<String>> facts,
        Map<String, List<AttachmentVO>> fileFields,
        List<String> allowedActions,
        List<ApprovalTaskVO> tasks,
        Long version,
        LocalDateTime submittedTime,
        LocalDateTime completedTime,
        LocalDateTime updatedTime) {
}
