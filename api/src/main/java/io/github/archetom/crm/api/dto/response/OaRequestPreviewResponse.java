package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record OaRequestPreviewResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long formId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") Long formVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long definitionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") Long definitionVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean autoApproved,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = ResolvedApprovalStepResponse.class))
        List<ResolvedApprovalStepResponse> steps) {
}
