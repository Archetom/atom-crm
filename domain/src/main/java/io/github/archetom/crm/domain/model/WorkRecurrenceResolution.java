package io.github.archetom.crm.domain.model;

import java.time.LocalDateTime;

/** The fixed local-calendar occurrence and its materialized UTC wall-clock value. */
public record WorkRecurrenceResolution(
        LocalDateTime nominalLocalTime,
        LocalDateTime resolvedLocalTime,
        int offsetSeconds,
        LocalDateTime resolvedUtcTime
) {
    public WorkRecurrenceResolution {
        if (nominalLocalTime == null || resolvedLocalTime == null || resolvedUtcTime == null) {
            throw new IllegalArgumentException("work recurrence resolution is invalid");
        }
    }
}
