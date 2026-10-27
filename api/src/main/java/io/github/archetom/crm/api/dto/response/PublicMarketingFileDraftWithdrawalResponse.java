package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Minimal acknowledgement that an anonymous FILE draft can no longer be used. */
public record PublicMarketingFileDraftWithdrawalResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long draftId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean withdrawn) {
}
