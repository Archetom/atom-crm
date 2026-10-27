package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record StageDefinitionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") int order,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", maximum = "100") int probability,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED), schema = @Schema(type = "string"))
        List<String> requiredFieldCodes,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED), schema = @Schema(implementation = StageTaskResponse.class))
        List<StageTaskResponse> tasks,
        Long approvalDefinitionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean editable,
        @Schema(description = "Stable reason why this legacy stage cannot be replaced as client-editable configuration.")
        String blockerCode) {
}
