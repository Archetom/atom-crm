package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApprovalPendingCountResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long count) {
}
