package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record ApprovalTaskVO(
        String taskKey,
        String sourceTaskKey,
        String stepCode,
        String stepName,
        int stepOrder,
        String mode,
        int actorOrder,
        Long assignedActorId,
        String status,
        String remark,
        LocalDateTime actedTime) {
}
