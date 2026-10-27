package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Minimal stage-field attachment projection; download remains separately authorized. */
public record StageFieldAttachmentResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long fileId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String mediaType,
        Long version) {
}
