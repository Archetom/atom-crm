package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;

public record StageWorkflowVO(
        Long id,
        String objectType,
        String name,
        String successName,
        String failureName,
        String status,
        List<Long> applicableActorIds,
        List<Long> applicableDepartmentIds,
        List<StageDefinitionVO> stages,
        Long version,
        LocalDateTime createdTime,
        LocalDateTime updatedTime) {
}
