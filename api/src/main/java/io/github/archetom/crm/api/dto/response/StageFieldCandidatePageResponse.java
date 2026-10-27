package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Paged candidates constrained by the workflow record, its current stage and field. */
public record StageFieldCandidatePageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") long pageNum,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") long pageSize,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long totalNum,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = StageFieldCandidateResponse.class))
        List<StageFieldCandidateResponse> objectList) {
}
