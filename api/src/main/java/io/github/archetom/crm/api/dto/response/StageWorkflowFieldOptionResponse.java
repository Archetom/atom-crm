package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** A field's current B05 write and stage-required-field eligibility. */
public record StageWorkflowFieldOptionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fieldType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean writable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean requiredFieldEligible,
        @Schema(description = "Stable reason when requiredFieldEligible is false.") String blockerCode) {
}
