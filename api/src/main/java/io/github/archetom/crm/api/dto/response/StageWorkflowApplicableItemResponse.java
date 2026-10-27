package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** One active workflow considered for the requested record. */
public record StageWorkflowApplicableItemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long workflowId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean canStart,
        @Schema(description = "Stable reason when canStart is false; null when it is true.")
        String blockerCode) {
}
