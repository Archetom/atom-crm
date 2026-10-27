package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** A FILE draft issued only after proving the runtime record and active stage field are writable. */
public record StageFileFieldDraftResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long draftId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long workflowRecordId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fieldCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") Long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime expiresAt) {
}
