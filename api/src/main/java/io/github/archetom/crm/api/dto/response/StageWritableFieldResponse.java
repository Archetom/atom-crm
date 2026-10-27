package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Renderable, currently writable field projection for the active stage form. */
public record StageWritableFieldResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fieldCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "TEXT", "TEXTAREA", "SELECT", "MULTI_SELECT", "DATE", "DATETIME",
                "INTEGER", "DECIMAL", "BOOLEAN", "EMAIL", "PHONE", "URL", "FILE",
                "USER", "DEPARTMENT", "RELATION", "ADDRESS"})
        String fieldType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean required,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = StageFieldOptionResponse.class))
        List<StageFieldOptionResponse> allowedValues,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(type = "string")) List<String> currentValues,
        @Schema(description = "Present only when fieldType is ADDRESS; currentValues remains empty for that field.",
                implementation = StageAddressValueResponse.class)
        StageAddressValueResponse currentAddress,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = StageFieldInputResponse.class)
        StageFieldInputResponse input,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Always false for writable fields: B05 forbids masked WRITE access.")
        boolean masked) {
}
