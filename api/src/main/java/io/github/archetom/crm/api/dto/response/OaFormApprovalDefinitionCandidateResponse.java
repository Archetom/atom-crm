package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** A runnable OA approval definition and the input facts its route requires. */
public record OaFormApprovalDefinitionCandidateResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> requiredFieldCodes) {
}
