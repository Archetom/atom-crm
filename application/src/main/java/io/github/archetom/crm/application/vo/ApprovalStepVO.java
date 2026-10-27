package io.github.archetom.crm.application.vo;

import java.util.List;

public record ApprovalStepVO(
        String code,
        String name,
        int order,
        String type,
        String mode,
        String fallback,
        List<Long> memberActorIds,
        List<Long> roleIds,
        List<ApprovalConditionVO> conditions) {
}
