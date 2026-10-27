package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record StageApprovalGateResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
        Long approvalInstanceId,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = ApprovalStartSelectionResponse.class))
        List<ApprovalStartSelectionResponse> approverSelections) {
}
