package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record StageWorkflowApplicableResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String objectType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long recordId,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = StageWorkflowApplicableItemResponse.class))
        List<StageWorkflowApplicableItemResponse> workflows) {
}
