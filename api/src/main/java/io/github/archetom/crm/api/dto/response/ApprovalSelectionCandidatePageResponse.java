package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Paginated server-authorized candidates for one applicant-select step. */
public record ApprovalSelectionCandidatePageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") long pageNum,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") long pageSize,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long totalNum,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = ApprovalSelectionCandidateResponse.class))
        List<ApprovalSelectionCandidateResponse> objectList) {
}
