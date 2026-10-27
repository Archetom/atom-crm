package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Full catalog for a caller who can manage stage workflow definitions. */
public record StageWorkflowConfigurationOptionsResponse(
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = StageWorkflowObjectTypeOptionResponse.class))
        List<StageWorkflowObjectTypeOptionResponse> objectTypes,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = StageWorkflowFieldOptionResponse.class))
        List<StageWorkflowFieldOptionResponse> fields,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = StageWorkflowApprovalDefinitionOptionResponse.class))
        List<StageWorkflowApprovalDefinitionOptionResponse> approvalDefinitions) {
}
