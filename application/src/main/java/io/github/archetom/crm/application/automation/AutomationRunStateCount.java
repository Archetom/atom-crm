package io.github.archetom.crm.application.automation;

/** Low-cardinality run-state snapshot value. */
public record AutomationRunStateCount(AutomationKind kind, AutomationRunStatus status, long count) {
    public AutomationRunStateCount {
        if (kind == null || status == null || count < 0) {
            throw new IllegalArgumentException("automation run state count is invalid");
        }
    }
}
