package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** A field the current OA-form manager may safely configure as applicant input. */
public record OaFormConfigurationFieldResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fieldCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fieldType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean required) {
}
