package io.github.archetom.crm.application.port.out.notification;

import java.time.Duration;

/**
 * Private external-effect boundary for one closed notification channel. An
 * implementation must honor the supplied deadline and interruption.
 */
public interface NotificationChannelAdapter {
    NotificationChannel channel();
    NotificationReadinessSnapshot readiness();
    /** Each fixed template needs independent evidence; adapters may support more than one by overriding this. */
    default NotificationReadinessSnapshot readiness(String templateRevision) {
        return readiness();
    }
    NotificationDispatchResult dispatch(NotificationDispatchCommand command, Duration deadline);
}
