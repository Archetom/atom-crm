package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Recent, current-member-only CRM import history with no source or failed-row data.
 *
 * <p>Carries the same page field names as every other paged response. It used to publish
 * {@code page}/{@code size}/{@code total}/{@code items}, which were the internal value object's
 * names rather than the API's, leaving a caller to handle two shapes depending on the endpoint.</p>
 */
public record CrmImportHistoryPageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") long pageNum,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") long pageSize,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long totalNum,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = CrmImportHistoryItemResponse.class))
        List<CrmImportHistoryItemResponse> objectList) {
}
