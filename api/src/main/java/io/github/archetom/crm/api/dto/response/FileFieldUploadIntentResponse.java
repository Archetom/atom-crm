package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/** A one-shot upload instruction for an exact staged FILE field candidate. */
public record FileFieldUploadIntentResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long draftId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fieldCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long fileId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uri") URI uploadUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "PUT") String uploadMethod,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Headers that must accompany the upload request.")
        Map<String, String> requiredHeaders,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time") Instant expiresAt) {
}
