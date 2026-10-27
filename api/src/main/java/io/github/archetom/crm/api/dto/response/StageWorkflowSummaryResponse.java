package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Lightweight definition projection for lists and selectors. */
public record StageWorkflowSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"CUSTOMER", "CONTACT", "LEAD", "OPPORTUNITY"}) String objectType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"ACTIVE", "INACTIVE", "DELETED"}) String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") Long version) {
}
