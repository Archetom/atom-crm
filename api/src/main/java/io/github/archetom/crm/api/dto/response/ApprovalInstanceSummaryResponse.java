package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ApprovalInstanceSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "PENDING", "APPROVED", "REJECTED", "WITHDRAWN", "CANCELLED"}) String status,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(allowableValues = {
                        "APPROVE", "REJECT", "TRANSFER", "WITHDRAW", "CANCEL"}))
        List<String> allowedActions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long version) {
}
