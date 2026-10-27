package io.github.archetom.crm.application.automation;

import java.util.List;

/** Result of the startup durable revision barrier. */
public record AutomationRevisionReconciliation(AutomationSchedulingReadiness readiness,
                                                List<AutomationRunTransition> transitions) {
    public AutomationRevisionReconciliation {
        if (readiness == null) throw new IllegalArgumentException("automation readiness is required");
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
    }
}
