package io.github.archetom.crm.application.port.out.notification;

/** Durable external-effect lifecycle, intentionally independent from inbox read state. */
public enum NotificationDeliveryStatus {
    READY,
    LEASED,
    DISPATCHING,
    RETRY_WAIT,
    ACCEPTED,
    REJECTED,
    SUPPRESSED,
    AMBIGUOUS,
    DEAD;

    public boolean terminal() {
        return switch (this) {
            case ACCEPTED, REJECTED, SUPPRESSED, AMBIGUOUS, DEAD -> true;
            default -> false;
        };
    }
}
