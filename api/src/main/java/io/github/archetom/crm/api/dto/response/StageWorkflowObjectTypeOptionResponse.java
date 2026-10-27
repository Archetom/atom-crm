package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record StageWorkflowObjectTypeOptionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"CUSTOMER", "CONTACT", "LEAD", "OPPORTUNITY"}) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName) {
}
