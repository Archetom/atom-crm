package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Closed recurrence rule used to generate future Work tasks. */
public record WorkTaskSeriesRecurrenceResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"DAILY", "WEEKLY", "MONTHLY"}) String frequency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") Integer interval,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(allowableValues = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY",
                        "FRIDAY", "SATURDAY", "SUNDAY"})) List<String> weekdays,
        @Schema(minimum = "1", maximum = "31") Integer monthDay,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean lastDayOfMonth,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String zoneId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate startLocalDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalTime localTime,
        LocalDate endLocalDate) {
}
