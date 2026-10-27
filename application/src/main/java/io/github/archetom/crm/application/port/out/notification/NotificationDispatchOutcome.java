package io.github.archetom.crm.application.port.out.notification;

/** Adapter result deliberately contains no provider message ID or provider error text. */
public enum NotificationDispatchOutcome {
    ACCEPTED,
    REJECTED,
    RETRYABLE_FAILURE,
    AMBIGUOUS
}
