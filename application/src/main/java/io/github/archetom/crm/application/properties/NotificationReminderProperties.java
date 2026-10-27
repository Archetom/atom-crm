package io.github.archetom.crm.application.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Deployment-only controls for the durable in-app reminder pump. */
@Data
@ConfigurationProperties(prefix = "atom.notifications.reminders")
public class NotificationReminderProperties {
    private static final Duration MIN_LEASE = Duration.ofSeconds(1);
    private static final Duration MAX_LEASE = Duration.ofMinutes(30);
    private static final Duration MIN_RETENTION = Duration.ofDays(1);
    private static final Duration MAX_RETENTION = Duration.ofDays(90);
    private static final Duration MIN_NOTIFICATION_RETENTION = Duration.ofDays(1);
    private static final Duration MAX_NOTIFICATION_RETENTION = Duration.ofDays(365);
    private static final Duration MIN_RETRY_DEADLINE = Duration.ofMinutes(1);
    private static final Duration MAX_RETRY_DEADLINE = Duration.ofDays(7);
    private static final Duration MIN_RETRY_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_RETRY_BACKOFF = Duration.ofHours(6);
    private boolean enabled = true;
    private int pumpConcurrency = 1;
    private Duration leaseDuration = Duration.ofMinutes(2);
    private Duration terminalRetention = Duration.ofDays(14);
    private Duration notificationRetention = Duration.ofDays(90);
    private int maxAttempts = 5;
    private Duration retryDeadline = Duration.ofDays(1);
    private Duration retryBackoff = Duration.ofMinutes(1);

    /** Task commands keep their transaction available even when the worker lane is closed. */
    public Duration safeRetryDeadline() {
        return within(retryDeadline, MIN_RETRY_DEADLINE, MAX_RETRY_DEADLINE)
                ? retryDeadline : Duration.ofDays(1);
    }

    public boolean valid() {
        return pumpConcurrency >= 1 && pumpConcurrency <= SchedulingProperties.MAX_LANE_CONCURRENCY
                && within(leaseDuration, MIN_LEASE, MAX_LEASE)
                && within(terminalRetention, MIN_RETENTION, MAX_RETENTION)
                && within(notificationRetention, MIN_NOTIFICATION_RETENTION, MAX_NOTIFICATION_RETENTION)
                && maxAttempts >= 1 && maxAttempts <= 20
                && within(retryDeadline, MIN_RETRY_DEADLINE, MAX_RETRY_DEADLINE)
                && within(retryBackoff, MIN_RETRY_BACKOFF, MAX_RETRY_BACKOFF)
                && retryBackoff.plus(leaseDuration).compareTo(retryDeadline) <= 0;
    }
    private static boolean within(Duration value, Duration min, Duration max) {
        return value != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }
}
