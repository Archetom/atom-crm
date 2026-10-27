package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** One-shot FILE draft baseline; form fields are present only for OA-origin drafts. */
public record FileFieldDraftResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String objectType,
        Long formId,
        Long formVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") Long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime expiresAt) { }
