package io.github.archetom.crm.domain.model;

/** Durable state of one exact task notification-projection step. */
public enum WorkTaskReminderOccurrenceStatus {
    READY,
    RETRY_WAIT,
    LEASED,
    EMITTED,
    CANCELLED,
    DEAD;

    public boolean terminal() {
        return this == EMITTED || this == CANCELLED || this == DEAD;
    }
}
