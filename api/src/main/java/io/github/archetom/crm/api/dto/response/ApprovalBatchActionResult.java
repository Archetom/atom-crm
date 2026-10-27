package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** A batch either applies every listed decision or applies none. */
public record ApprovalBatchActionResult(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Always true: the batch is all-or-nothing.") boolean atomic,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True only when every item was persisted; false means no item changed.")
        boolean applied,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = ApprovalBatchActionItemResponse.class))
        List<ApprovalBatchActionItemResponse> items) {
}
