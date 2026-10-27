package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record ProductImageUploadIntentResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long draftId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"PRIMARY", "DETAIL"}) String imageType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long fileId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) URI uploadUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String method,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, String> headers,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time") Instant expiresAt) { }
