package io.github.archetom.crm.application.port.out.notification;

/** Provider result classification only; detail stays private to an adapter's secured diagnostics. */
public record NotificationDispatchResult(NotificationDispatchOutcome outcome) {
    public NotificationDispatchResult {
        if (outcome == null) throw new IllegalArgumentException("notification dispatch outcome is required");
    }

    public static NotificationDispatchResult accepted() { return new NotificationDispatchResult(NotificationDispatchOutcome.ACCEPTED); }
    public static NotificationDispatchResult rejected() { return new NotificationDispatchResult(NotificationDispatchOutcome.REJECTED); }
    public static NotificationDispatchResult retryableFailure() { return new NotificationDispatchResult(NotificationDispatchOutcome.RETRYABLE_FAILURE); }
    public static NotificationDispatchResult ambiguous() { return new NotificationDispatchResult(NotificationDispatchOutcome.AMBIGUOUS); }
}
