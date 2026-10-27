package io.github.archetom.crm.api.dto.response;

import java.util.List;

public record ApprovalStepResponse(String code, String name, int order, String type,
                                   String mode, String fallback, List<Long> memberActorIds,
                                   List<Long> roleIds,
                                   List<ApprovalConditionResponse> conditions) {
}
