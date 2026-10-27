package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Minimal public acknowledgement; it exposes no lead, tenant, owner, or form internals. */
public record MarketingFormSubmissionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long submissionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean accepted,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String idempotencyKey) {
}
