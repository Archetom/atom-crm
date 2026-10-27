package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** A currently submitable outcome. The server revalidates it on write. */
public record StageOutcomeOptionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"FAILED"}) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean remarkRequired,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") int remarkMaxLength) {
}
