package io.github.archetom.crm.application.automation;

import java.util.List;
import java.util.Optional;

/** Claim cleanup can terminalize older runs before returning at most one lease. */
public record AutomationClaimResult(Optional<AutomationExecutionContext> context,
                                    List<AutomationRunTransition> transitions) {
    public AutomationClaimResult {
        context = context == null ? Optional.empty() : context;
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
    }

    public static AutomationClaimResult empty() {
        return new AutomationClaimResult(Optional.empty(), List.of());
    }
}
