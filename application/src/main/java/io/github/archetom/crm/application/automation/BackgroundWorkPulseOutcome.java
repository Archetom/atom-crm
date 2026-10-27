package io.github.archetom.crm.application.automation;

/** Bounded scheduler-invocation outcome used only for low-cardinality observation. */
public enum BackgroundWorkPulseOutcome {
    SUBMITTED,
    SKIPPED_IN_FLIGHT,
    REJECTED,
    COMPLETED,
    FAILED
}
