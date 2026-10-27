package io.github.archetom.crm.api.dto.response;

import java.util.List;

public record ApprovalStartOptionsResponse(String objectType, Long subjectId,
                                           List<ApprovalStartOptionResponse> options) {
}
