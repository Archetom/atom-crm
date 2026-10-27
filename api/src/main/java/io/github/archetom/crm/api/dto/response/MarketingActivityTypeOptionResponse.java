package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** One server-defined category available to a marketing-activity editor. */
public record MarketingActivityTypeOptionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"ADVERTISING", "SEMINAR", "CONFERENCE", "EMAIL_CAMPAIGN",
                        "PUBLIC_RELATIONS", "PARTNER"})
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String displayName) {
}
