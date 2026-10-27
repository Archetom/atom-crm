package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Server-filtered marketing-form field choices; no raw field IDs or local catalog inference. */
public record MarketingFormConfigurationOptionsResponse(
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = MarketingFormConfigurationFieldResponse.class))
        List<MarketingFormConfigurationFieldResponse> fields) {
    public MarketingFormConfigurationOptionsResponse {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
