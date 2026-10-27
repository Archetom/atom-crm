package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** One server-defined related-object capability available to a marketing-activity editor. */
public record MarketingActivityRelatedObjectTypeOptionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"CUSTOMER", "CONTACT", "LEAD"})
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean supportsMarketingForm) {
}
