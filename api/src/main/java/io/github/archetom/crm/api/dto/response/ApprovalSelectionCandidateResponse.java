package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** A server-authorized member that may be selected for one applicant-select step. */
public record ApprovalSelectionCandidateResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long actorId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName) {
}
