package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** Full configuration projection, including server-calculated management capabilities. */
public record StageWorkflowDetailResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"CUSTOMER", "CONTACT", "LEAD", "OPPORTUNITY"}) String objectType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String successName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String failureName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"ACTIVE", "INACTIVE", "DELETED"}) String status,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED), schema = @Schema(minimum = "1"))
        List<Long> applicableActorIds,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED), schema = @Schema(minimum = "1"))
        List<Long> applicableDepartmentIds,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = StageDefinitionResponse.class)) List<StageDefinitionResponse> stages,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(allowableValues = {"UPDATE", "CHANGE_STATUS"})) List<String> allowedActions,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Allowed status targets. Empty unless allowedActions includes CHANGE_STATUS."),
                schema = @Schema(allowableValues = {"ACTIVE", "INACTIVE", "DELETED"}))
        List<String> allowedStatuses,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") Long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime createdTime,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime updatedTime) {
}
