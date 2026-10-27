package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** A server-authorized lead field available to a marketing-form owner. */
public record MarketingFormConfigurationFieldResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String fieldCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "TEXT", "TEXTAREA", "SELECT", "MULTI_SELECT", "DATE", "DATETIME",
                "INTEGER", "DECIMAL", "BOOLEAN", "EMAIL", "PHONE", "URL", "FILE"})
        String fieldType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean required,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = MarketingFormFieldOptionResponse.class))
        List<MarketingFormFieldOptionResponse> options) {
    public MarketingFormConfigurationFieldResponse {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
