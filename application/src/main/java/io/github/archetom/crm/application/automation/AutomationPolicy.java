package io.github.archetom.crm.application.automation;

import java.time.Duration;

/** Validated deployment policy; no tenant-configurable schedules are supported. */
public record AutomationPolicy(
        boolean enabled,
        boolean valid,
        String cadenceRevision,
        Duration poolCadence,
        int poolRecordLimit,
        Duration recurrenceCadence,
        int recurrenceRecordLimit,
        Duration leaseDuration,
        Duration runDeadline,
        int maxAttempts,
        Duration retryBackoff,
        Duration terminalRetention) {

    public AutomationPolicy {
        if (cadenceRevision == null || !cadenceRevision.matches("B26A_V[1-9][0-9]{0,8}")
                || poolCadence == null || poolRecordLimit < 1 || recurrenceCadence == null
                || recurrenceRecordLimit < 1 || leaseDuration == null
                || runDeadline == null || maxAttempts < 1 || retryBackoff == null
                || terminalRetention == null) {
            throw new IllegalArgumentException("automation policy is invalid");
        }
    }

    /** Monotonic deployment revision used to reject an older mixed-version replica. */
    public long cadenceRevisionNumber() {
        return Long.parseLong(cadenceRevision.substring("B26A_V".length()));
    }

    /** Compatibility constructor for B26 callers and fixtures. */
    public AutomationPolicy(boolean enabled, boolean valid, String cadenceRevision,
                            Duration poolCadence, int poolRecordLimit, Duration leaseDuration,
                            Duration runDeadline, int maxAttempts, Duration retryBackoff,
                            Duration terminalRetention) {
        this(enabled, valid, cadenceRevision, poolCadence, poolRecordLimit,
                Duration.ofMinutes(5), 100, leaseDuration, runDeadline, maxAttempts,
                retryBackoff, terminalRetention);
    }

    public Duration cadenceFor(AutomationKind kind) {
        if (kind == null) throw new IllegalArgumentException("automation kind is required");
        return kind == AutomationKind.WORK_TASK_RECURRENCE ? recurrenceCadence : poolCadence;
    }
}
