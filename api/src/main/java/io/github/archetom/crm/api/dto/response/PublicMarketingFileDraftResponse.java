package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One-time anonymous FILE draft capability. The capability is kept only in browser memory and
 * must be sent as {@code X-Public-Upload-Capability}; it is not an HTTP authentication token.
 */
public record PublicMarketingFileDraftResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long draftId,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED)) List<String> fieldCodes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "One-time, short-lived upload capability. Never persist, log, or place it in a URL.")
        String uploadCapability,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time") LocalDateTime expiresAt) {
}
