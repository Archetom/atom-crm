package io.github.archetom.crm.application.automation;

/** A bounded ledger transition; it deliberately carries no tenant or run identity. */
public record AutomationRunTransition(AutomationKind kind, AutomationRunMetric metric, long count) {
    public AutomationRunTransition {
        if (kind == null || metric == null || count < 1) {
            throw new IllegalArgumentException("automation run transition is invalid");
        }
    }
}
