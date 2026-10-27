package io.github.archetom.crm.application.automation;

/** Fixed-cardinality recurrence effect outcomes, recorded only after commit. */
public enum WorkRecurrenceOutcome {
    MATERIALIZED,
    SKIPPED,
    PAUSED
}
