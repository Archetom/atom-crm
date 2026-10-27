package io.github.archetom.crm.application.automation;

/** Deployment-owned revision and time-zone database evidence for B27a. */
public record WorkRecurrencePolicy(
        boolean enabled,
        boolean valid,
        String policyRevision,
        String tzdbFingerprint
) {
    public WorkRecurrencePolicy {
        if (policyRevision == null || !policyRevision.matches("B27A_V[1-9][0-9]{0,8}")
                || tzdbFingerprint == null || !tzdbFingerprint.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("work recurrence policy is invalid");
        }
    }

    public long revisionNumber() {
        return Long.parseLong(policyRevision.substring("B27A_V".length()));
    }
}
