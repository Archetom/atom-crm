package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;

public record ApprovalInstanceSummaryVO(
        Long id,
        Long definitionId,
        Long definitionVersion,
        String objectType,
        Long subjectId,
        String title,
        String status,
        Integer currentStepOrder,
        List<String> allowedActions,
        Long version,
        LocalDateTime submittedTime,
        LocalDateTime completedTime,
        LocalDateTime updatedTime) {
}
