package io.github.archetom.crm.application.port.out.notification;

/** Provider operation guarantee; this is never a generic exactly-once claim. */
public enum NotificationDispatchGuaranteeKind {
    IDEMPOTENT_SEND,
    AT_MOST_ONCE_SEND,
    UNSUPPORTED
}
