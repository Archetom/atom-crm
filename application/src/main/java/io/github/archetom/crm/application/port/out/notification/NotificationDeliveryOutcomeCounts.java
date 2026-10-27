package io.github.archetom.crm.application.port.out.notification;

/** Terminal ledger transitions committed by one bounded maintenance or task mutation operation. */
public record NotificationDeliveryOutcomeCounts(int suppressed, int ambiguous, int dead) {
    public NotificationDeliveryOutcomeCounts {
        if (suppressed < 0 || ambiguous < 0 || dead < 0) {
            throw new IllegalArgumentException("notification delivery outcome counts are invalid");
        }
    }

    public static NotificationDeliveryOutcomeCounts none() {
        return new NotificationDeliveryOutcomeCounts(0, 0, 0);
    }

    public NotificationDeliveryOutcomeCounts plus(NotificationDeliveryOutcomeCounts other) {
        if (other == null) return this;
        return new NotificationDeliveryOutcomeCounts(suppressed + other.suppressed,
                ambiguous + other.ambiguous, dead + other.dead);
    }
}
