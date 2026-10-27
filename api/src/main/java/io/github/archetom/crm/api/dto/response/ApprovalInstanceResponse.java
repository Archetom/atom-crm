package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Caller-safe approval projection.  Capability, not task assignee or applicant identity,
 * determines which controls a client may render.
 */
public record ApprovalInstanceResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "PENDING", "APPROVED", "REJECTED", "WITHDRAWN", "CANCELLED"}) String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(allowableValues = {
                        "APPROVE", "REJECT", "TRANSFER", "WITHDRAW", "CANCEL"}))
        List<String> allowedActions,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = ApprovalTaskResponse.class))
        List<ApprovalTaskResponse> tasks) {
}
