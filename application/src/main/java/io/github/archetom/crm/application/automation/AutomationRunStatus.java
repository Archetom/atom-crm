package io.github.archetom.crm.application.automation;

/** Durable scheduled-automation run lifecycle. */
public enum AutomationRunStatus {
    READY,
    LEASED,
    RETRY_WAIT,
    SUCCEEDED,
    CANCELLED,
    DEAD;

    public boolean terminal() {
        return this == SUCCEEDED || this == CANCELLED || this == DEAD;
    }
}
