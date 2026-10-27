package io.github.archetom.crm.application.automation;

/** Fixed lifecycle counters for durable Work-task reminder occurrences. */
public enum WorkTaskReminderMetric {
    CLAIMED,
    EMITTED,
    CANCELLED,
    DEAD
}
