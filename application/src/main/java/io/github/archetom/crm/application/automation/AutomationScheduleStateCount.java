package io.github.archetom.crm.application.automation;

/** Low-cardinality schedule-state snapshot value. */
public record AutomationScheduleStateCount(AutomationKind kind, AutomationScheduleStatus status, long count) {
    public AutomationScheduleStateCount {
        if (kind == null || status == null || count < 0) {
            throw new IllegalArgumentException("automation schedule state count is invalid");
        }
    }
}
