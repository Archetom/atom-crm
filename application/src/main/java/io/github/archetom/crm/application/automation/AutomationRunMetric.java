package io.github.archetom.crm.application.automation;

/** Safe, fixed result categories for a claimed automation occurrence. */
public enum AutomationRunMetric {
    MATERIALIZED,
    CLAIMED,
    SUCCEEDED,
    RETRY,
    DEAD,
    CANCELLED
}
