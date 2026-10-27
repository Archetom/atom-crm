package io.github.archetom.crm.domain.model;

import java.time.LocalDate;

/** One database-aggregated accepted-submission count for a calendar period. */
public record MarketingFormSubmissionTrendPoint(
        LocalDate periodStart, long acceptedCount) {

    public MarketingFormSubmissionTrendPoint {
        if (periodStart == null || acceptedCount < 0) {
            throw new IllegalArgumentException(
                    "marketing form submission trend point is invalid");
        }
    }
}
