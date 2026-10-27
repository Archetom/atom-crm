package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Typed wire projection for transfer candidates the caller may select. */
public record ApprovalTransferCandidatePageResponse(
        @Schema(minimum = "1") long pageNum,
        @Schema(minimum = "1") long pageSize,
        @Schema(minimum = "0") long totalNum,
        @ArraySchema(schema = @Schema(implementation = ApprovalTransferCandidateResponse.class))
        List<ApprovalTransferCandidateResponse> objectList) {
}
