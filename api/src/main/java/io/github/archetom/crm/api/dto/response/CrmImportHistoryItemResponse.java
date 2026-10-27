package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Source-free summary of a completed CSV import owned by the authenticated member. */
public record CrmImportHistoryItemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long draftId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime completedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") Integer importedRows) { }
