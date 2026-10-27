package io.github.archetom.crm.application.automation;

/** Fixed-cardinality availability state for the Work recurrence lane only. */
public enum WorkRecurrenceReadiness {
    READY,
    DISABLED,
    CONFIG_INVALID,
    NOT_RECONCILED,
    RECONCILING,
    REVISION_DRIFT,
    TZDB_DRIFT
}
