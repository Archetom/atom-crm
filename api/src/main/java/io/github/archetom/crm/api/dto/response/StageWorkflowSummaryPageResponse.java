package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Typed wire projection for a stage-workflow definition page. */
public record StageWorkflowSummaryPageResponse(
        @Schema(minimum = "1") long pageNum,
        @Schema(minimum = "1") long pageSize,
        @Schema(minimum = "0") long totalNum,
        @ArraySchema(schema = @Schema(implementation = StageWorkflowSummaryResponse.class))
        List<StageWorkflowSummaryResponse> objectList) {
}
