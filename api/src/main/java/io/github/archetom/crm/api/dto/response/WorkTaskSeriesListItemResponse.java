package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Minimal visible Work task series projection for a page. */
public record WorkTaskSeriesListItemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") Long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkTaskSeriesStatusResponse status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = WorkTaskSeriesRecurrenceResponse.class) WorkTaskSeriesRecurrenceResponse recurrence,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = WorkTaskSeriesActionResponse.class))
        List<WorkTaskSeriesActionResponse> allowedActions,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = WorkTaskSeriesActionBlockerResponse.class))
        List<WorkTaskSeriesActionBlockerResponse> actionBlockers) {
}
