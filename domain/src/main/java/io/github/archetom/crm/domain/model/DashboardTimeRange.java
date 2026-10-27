package io.github.archetom.crm.domain.model;

/** Relative or bounded custom time window selected for the current member's dashboard. */
public enum DashboardTimeRange {
    TODAY,
    CURRENT_WEEK,
    CURRENT_MONTH,
    CURRENT_QUARTER,
    CURRENT_YEAR,
    CUSTOM
}
