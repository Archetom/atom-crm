package io.github.archetom.crm.api.dto.response;

import java.util.List;

public record ApprovalPreviewResponse(Long definitionId, Long definitionVersion,
                                      Long subjectId, boolean executable, String blockerCode,
                                      List<ApprovalPreviewStepResponse> steps) {
}
