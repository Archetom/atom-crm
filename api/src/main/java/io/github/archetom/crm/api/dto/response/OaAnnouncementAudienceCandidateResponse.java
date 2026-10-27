package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** One active tenant member or department eligible for an OA announcement audience selector. */
public record OaAnnouncementAudienceCandidateResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName) {
}
