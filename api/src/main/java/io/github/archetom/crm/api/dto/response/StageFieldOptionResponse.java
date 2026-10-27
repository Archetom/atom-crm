package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Stable value and presentation label for one selectable stage form field option. */
public record StageFieldOptionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String value,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label) {
}
