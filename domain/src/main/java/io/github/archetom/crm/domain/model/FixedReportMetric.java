package io.github.archetom.crm.domain.model;

import java.util.Locale;

/** Server-owned B17b metrics; request text can only select one of these values. */
public enum FixedReportMetric {
    WON_OPPORTUNITY,
    ACTIVE_CONTRACT,
    ACTIVE_RECEIVABLE;

    public static FixedReportMetric parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("report metric is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("report metric is invalid", exception);
        }
    }
}
