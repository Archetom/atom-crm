package io.github.archetom.crm.api.dto.response;

import java.util.List;

public record ResolvedApprovalStepResponse(String code, String name, int order,
                                           String mode, List<Long> assigneeActorIds) {
}
