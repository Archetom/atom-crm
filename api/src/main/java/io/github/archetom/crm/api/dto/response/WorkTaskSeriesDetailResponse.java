package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** Versioned editing baseline for one visible Work task series. */
public record WorkTaskSeriesDetailResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") Long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"LOW", "NORMAL", "HIGH", "URGENT"}) String priority,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Long assigneeActorId,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(type = "integer", format = "int64", minimum = "1"))
        List<Long> followerActorIds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkTaskSeriesStatusResponse status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = WorkTaskSeriesRecurrenceResponse.class) WorkTaskSeriesRecurrenceResponse recurrence,
        LocalDateTime nextNominalLocalTime,
        LocalDateTime nextDueTime,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = WorkTaskSeriesActionResponse.class))
        List<WorkTaskSeriesActionResponse> allowedActions,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = WorkTaskSeriesActionBlockerResponse.class))
        List<WorkTaskSeriesActionBlockerResponse> actionBlockers) {
}
