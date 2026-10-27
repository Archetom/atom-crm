package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;

/** Upload instruction for one anonymous MarketingForm FILE candidate. */
public record PublicMarketingUploadIntentResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long draftId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fieldCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long fileId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uri") URI uploadUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "PUT") String uploadMethod,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Non-secret headers required for the binary upload.")
        Map<String, String> requiredHeaders,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Header name that receives the one-time uploadCapability from the draft response.")
        String capabilityHeaderName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") Long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String mediaType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time") LocalDateTime expiresAt) {
}
