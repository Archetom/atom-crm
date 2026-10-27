package io.github.archetom.crm.application.vo;

import java.util.List;

public record ResolvedApprovalStepVO(
        String code,
        String name,
        int order,
        String mode,
        List<Long> assigneeActorIds) {
}
