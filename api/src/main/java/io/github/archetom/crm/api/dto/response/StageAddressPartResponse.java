package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** One element in the structured ADDRESS input schema. */
public record StageAddressPartResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean required,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") int maxLength) {
}
