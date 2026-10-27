package io.github.archetom.crm.application.properties;

import io.github.archetom.crm.application.automation.AutomationPolicy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Deployment-only controls for the B26 scheduled-automation lane. */
@Data
@ConfigurationProperties(prefix = "atom.scheduling")
public class SchedulingProperties {
    public static final int MAX_LANE_CONCURRENCY = 16;
    private static final Duration MIN_PULSE_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_PULSE_DELAY = Duration.ofMinutes(5);
    private static final Duration MIN_POOL_CADENCE = Duration.ofMinutes(1);
    private static final Duration MAX_POOL_CADENCE = Duration.ofDays(7);
    private static final Duration MIN_LEASE_DURATION = Duration.ofSeconds(1);
    private static final Duration MAX_LEASE_DURATION = Duration.ofMinutes(30);
    private static final Duration MIN_RUN_DEADLINE = Duration.ofMinutes(1);
    private static final Duration MAX_RUN_DEADLINE = Duration.ofDays(7);
    private static final Duration MIN_RETRY_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_RETRY_BACKOFF = Duration.ofHours(6);
    private static final Duration MIN_TERMINAL_RETENTION = Duration.ofDays(1);
    private static final Duration MAX_TERMINAL_RETENTION = Duration.ofDays(90);

    private boolean enabled = false;
    /** Emergency/test switch for all local scheduled wake-ups; durable ledgers retain recovery state. */
    private boolean pulsesEnabled = true;
    private Duration pulseDelay = Duration.ofSeconds(5);
    private int automationLaneConcurrency = 1;
    private int attachmentLaneConcurrency = 2;
    private String cadenceRevision = "B26A_V2";
    private Duration poolCadence = Duration.ofHours(1);
    private int poolRecordLimit = 200;
    private Duration recurrenceCadence = Duration.ofMinutes(5);
    private int recurrenceRecordLimit = 100;
    private Duration leaseDuration = Duration.ofMinutes(2);
    private Duration runDeadline = Duration.ofHours(24);
    private int maxAttempts = 5;
    private Duration retryBackoff = Duration.ofMinutes(1);
    private Duration terminalRetention = Duration.ofDays(14);

    public AutomationPolicy policy() {
        boolean valid = within(pulseDelay, MIN_PULSE_DELAY, MAX_PULSE_DELAY)
                && withinConcurrency(automationLaneConcurrency) && withinConcurrency(attachmentLaneConcurrency)
                && cadenceRevision != null && cadenceRevision.matches("B26A_V[1-9][0-9]{0,8}")
                && within(poolCadence, MIN_POOL_CADENCE, MAX_POOL_CADENCE)
                && poolRecordLimit > 0 && poolRecordLimit <= 1_000
                && within(leaseDuration, MIN_LEASE_DURATION, MAX_LEASE_DURATION)
                && within(runDeadline, MIN_RUN_DEADLINE, MAX_RUN_DEADLINE)
                && runDeadline.compareTo(leaseDuration) > 0
                && maxAttempts > 0 && maxAttempts <= 20
                && within(retryBackoff, MIN_RETRY_BACKOFF, MAX_RETRY_BACKOFF)
                && retryBackoff.plus(leaseDuration).compareTo(runDeadline) <= 0
                && within(terminalRetention, MIN_TERMINAL_RETENTION, MAX_TERMINAL_RETENTION);
        return new AutomationPolicy(enabled, valid,
                valid ? cadenceRevision : "B26A_V2",
                valid ? poolCadence : Duration.ofHours(1),
                valid ? poolRecordLimit : 200,
                recurrenceValid() ? recurrenceCadence : Duration.ofMinutes(5),
                recurrenceValid() ? recurrenceRecordLimit : 100,
                valid ? leaseDuration : Duration.ofMinutes(2),
                valid ? runDeadline : Duration.ofHours(24),
                valid ? maxAttempts : 5,
                valid ? retryBackoff : Duration.ofMinutes(1),
                valid ? terminalRetention : Duration.ofDays(14));
    }

    /** Invalid recurrence deployment values close only the WORK_TASK_RECURRENCE kind. */
    public boolean recurrenceValid() {
        return within(recurrenceCadence, MIN_POOL_CADENCE, MAX_POOL_CADENCE)
                && recurrenceRecordLimit > 0 && recurrenceRecordLimit <= 1_000;
    }

    private static boolean withinConcurrency(int value) {
        return value >= 1 && value <= MAX_LANE_CONCURRENCY;
    }

    private static boolean within(Duration value, Duration minimum, Duration maximum) {
        return value != null && value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }
}
