package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Safe import completion projection; target facts are refreshed through their ordinary queries. */
public record PerformanceTargetImportResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") int importedCount) { }
