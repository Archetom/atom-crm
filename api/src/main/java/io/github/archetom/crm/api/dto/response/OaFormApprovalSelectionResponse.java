package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** One applicant-select approval step available while submitting an OA form. */
public record OaFormApprovalSelectionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "ALL", "ANY", "SEQUENTIAL"}) String mode) {
}
