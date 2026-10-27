package io.github.archetom.crm.application.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Deployment controls for the OA-owned one-shot publication lane. */
@Data
@ConfigurationProperties(prefix = "atom.oa.announcements.publication")
public class OaAnnouncementPublicationProperties {
    private static final Duration MIN_LEASE = Duration.ofSeconds(1);
    private static final Duration MAX_LEASE = Duration.ofMinutes(30);
    private static final Duration MIN_LATENESS = Duration.ofMinutes(1);
    private static final Duration MAX_LATENESS = Duration.ofDays(7);
    private static final Duration MIN_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_BACKOFF = Duration.ofHours(6);
    private static final Duration MIN_RETENTION = Duration.ofDays(1);
    private static final Duration MAX_RETENTION = Duration.ofDays(90);

    private boolean enabled;
    private int pumpConcurrency = 1;
    private Duration leaseDuration = Duration.ofMinutes(2);
    private Duration maxLateness = Duration.ofDays(1);
    private int maxAttempts = 5;
    private Duration retryBackoff = Duration.ofMinutes(1);
    private Duration terminalRetention = Duration.ofDays(14);

    public boolean valid() {
        return pumpConcurrency >= 1 && pumpConcurrency <= SchedulingProperties.MAX_LANE_CONCURRENCY
                && within(leaseDuration, MIN_LEASE, MAX_LEASE)
                && within(maxLateness, MIN_LATENESS, MAX_LATENESS)
                && maxAttempts >= 1 && maxAttempts <= 20
                && within(retryBackoff, MIN_BACKOFF, MAX_BACKOFF)
                && retryBackoff.plus(leaseDuration).compareTo(maxLateness) <= 0
                && within(terminalRetention, MIN_RETENTION, MAX_RETENTION);
    }

    public boolean acceptingSchedules(boolean pulsesEnabled) {
        return enabled && valid() && pulsesEnabled;
    }

    private static boolean within(Duration value, Duration minimum, Duration maximum) {
        return value != null && value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }
}
