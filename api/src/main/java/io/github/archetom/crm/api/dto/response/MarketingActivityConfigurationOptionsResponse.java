package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Server-published choices for the marketing-activity creation and edit flow. */
public record MarketingActivityConfigurationOptionsResponse(
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = MarketingActivityTypeOptionResponse.class))
        List<MarketingActivityTypeOptionResponse> activityTypes,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = MarketingActivityRelatedObjectTypeOptionResponse.class))
        List<MarketingActivityRelatedObjectTypeOptionResponse> relatedObjectTypes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"CUSTOMER", "CONTACT", "LEAD"})
        String defaultRelatedObjectType) {
    public MarketingActivityConfigurationOptionsResponse {
        activityTypes = activityTypes == null ? List.of() : List.copyOf(activityTypes);
        relatedObjectTypes = relatedObjectTypes == null ? List.of() : List.copyOf(relatedObjectTypes);
    }
}
