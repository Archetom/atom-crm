package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** One item of an atomic batch decision.  errCode is stable and intentionally message-free. */
public record ApprovalBatchActionItemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long instanceId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "APPLIED", "REJECTED", "NOT_APPLIED"}) String status,
        Long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "NONE", "VERSION_CONFLICT", "APPROVAL_ACTION_NOT_ALLOWED",
                "TRANSFER_TARGET_INVALID", "BATCH_ABORTED"}) String errCode) {
}
