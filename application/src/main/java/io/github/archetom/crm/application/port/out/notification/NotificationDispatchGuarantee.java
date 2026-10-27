package io.github.archetom.crm.application.port.out.notification;

import java.time.Duration;

/** Bounded provider promise captured at dispatch admission. */
public record NotificationDispatchGuarantee(
        NotificationDispatchGuaranteeKind kind, Duration idempotencyWindow) {

    public NotificationDispatchGuarantee {
        if (kind == null) throw new IllegalArgumentException("notification dispatch guarantee is required");
        if (kind == NotificationDispatchGuaranteeKind.IDEMPOTENT_SEND
                && (idempotencyWindow == null || idempotencyWindow.isNegative() || idempotencyWindow.isZero())) {
            throw new IllegalArgumentException("idempotent notification dispatch window is invalid");
        }
        if (kind != NotificationDispatchGuaranteeKind.IDEMPOTENT_SEND && idempotencyWindow != null) {
            throw new IllegalArgumentException("notification dispatch window is unsupported");
        }
    }

    public static NotificationDispatchGuarantee unsupported() {
        return new NotificationDispatchGuarantee(NotificationDispatchGuaranteeKind.UNSUPPORTED, null);
    }

    public static NotificationDispatchGuarantee atMostOnce() {
        return new NotificationDispatchGuarantee(NotificationDispatchGuaranteeKind.AT_MOST_ONCE_SEND, null);
    }

    public static NotificationDispatchGuarantee idempotent(Duration window) {
        return new NotificationDispatchGuarantee(NotificationDispatchGuaranteeKind.IDEMPOTENT_SEND, window);
    }
}
