package io.github.archetom.crm.application.automation;

/** Bounded, safe result of a scheduled handler. */
public record AutomationOutcome(String resultCode, int affectedCount) {
    public AutomationOutcome {
        if (resultCode == null || !resultCode.matches("[A-Z_]{1,40}") || affectedCount < 0) {
            throw new IllegalArgumentException("automation outcome is invalid");
        }
    }

    public static AutomationOutcome released(int affectedCount) {
        return new AutomationOutcome("RELEASED", affectedCount);
    }
}
