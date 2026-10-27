package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;

public record ApprovalDefinitionVO(
        Long id,
        String objectType,
        String name,
        String description,
        String status,
        String repeatMode,
        List<Long> initiatorActorIds,
        List<Long> initiatorDepartmentIds,
        List<Long> managerActorIds,
        List<ApprovalStepVO> steps,
        Long version,
        LocalDateTime createdTime,
        LocalDateTime updatedTime) {
}
