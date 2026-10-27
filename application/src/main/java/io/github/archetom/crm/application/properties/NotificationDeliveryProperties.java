package io.github.archetom.crm.application.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/** Deployment-only controls for the EMAIL delivery ledger; disabled is the production-safe default. */
@Data
@ConfigurationProperties(prefix = "atom.notifications.channels.email")
public class NotificationDeliveryProperties {
    private static final Duration MIN_LEASE = Duration.ofSeconds(1);
    private static final Duration MAX_LEASE = Duration.ofMinutes(30);
    private static final Duration MIN_DEADLINE = Duration.ofMinutes(1);
    private static final Duration MAX_DEADLINE = Duration.ofDays(7);
    private static final Duration MIN_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_BACKOFF = Duration.ofHours(6);
    private static final Duration MIN_DISPATCH_DEADLINE = Duration.ofSeconds(1);
    private static final Duration MAX_DISPATCH_DEADLINE = Duration.ofMinutes(30);
    private static final Duration MIN_RETENTION = Duration.ofDays(1);
    private static final Duration MAX_RETENTION = Duration.ofDays(365);
    private static final Duration MIN_SETTLEMENT_RESERVE = Duration.ofMillis(100);
    private static final Duration MAX_SETTLEMENT_RESERVE = Duration.ofSeconds(30);

    private boolean enabled;
    private int pumpConcurrency = 1;
    private Duration leaseDuration = Duration.ofMinutes(2);
    private int maxAttempts = 5;
    private Duration retryDeadline = Duration.ofDays(1);
    private Duration retryBackoff = Duration.ofMinutes(1);
    private Duration dispatchDeadline = Duration.ofSeconds(30);
    private Duration settlementReserve = Duration.ofSeconds(1);
    private Duration terminalRetention = Duration.ofDays(30);
    /** Deployment-pinned adapter identity; all three must match readiness evidence exactly. */
    private String adapterContract;
    private String adapterBuild;
    private String bindingRevision;
    /** Active key version plus a deployment-owned keyring permit safe endpoint-binding key rotation. */
    private String activeEndpointBindingKeyVersion;
    private Map<String, String> endpointBindingKeys = Map.of();

    public Duration safeRetryDeadline() {
        return within(retryDeadline, MIN_DEADLINE, MAX_DEADLINE) ? retryDeadline : Duration.ofDays(1);
    }

    public Duration safeTerminalRetention() {
        return within(terminalRetention, MIN_RETENTION, MAX_RETENTION) ? terminalRetention : Duration.ofDays(30);
    }

    public Duration safeTerminalRetention(Duration notificationRetention) {
        Duration retention = safeTerminalRetention();
        return notificationRetention != null && !notificationRetention.isNegative() && !notificationRetention.isZero()
                && retention.compareTo(notificationRetention) > 0 ? notificationRetention : retention;
    }

    public boolean dispatchEnabled() {
        return enabled && valid();
    }

    public boolean dispatchEnabled(Duration notificationRetention) {
        return enabled && valid() && notificationRetention != null && !notificationRetention.isNegative()
                && !notificationRetention.isZero() && terminalRetention.compareTo(notificationRetention) <= 0;
    }

    public String endpointBindingKey(String version) {
        if (version == null || endpointBindingKeys == null) return null;
        String key = endpointBindingKeys.get(version);
        return key == null || key.isBlank() ? null : key;
    }

    public boolean valid() {
        return pumpConcurrency >= 1 && pumpConcurrency <= SchedulingProperties.MAX_LANE_CONCURRENCY
                && within(leaseDuration, MIN_LEASE, MAX_LEASE)
                && maxAttempts >= 1 && maxAttempts <= 20
                && within(retryDeadline, MIN_DEADLINE, MAX_DEADLINE)
                && within(retryBackoff, MIN_BACKOFF, MAX_BACKOFF)
                && within(dispatchDeadline, MIN_DISPATCH_DEADLINE, MAX_DISPATCH_DEADLINE)
                && within(settlementReserve, MIN_SETTLEMENT_RESERVE, MAX_SETTLEMENT_RESERVE)
                && within(terminalRetention, MIN_RETENTION, MAX_RETENTION)
                && retryBackoff.plus(leaseDuration).compareTo(retryDeadline) <= 0
                && dispatchDeadline.compareTo(leaseDuration) <= 0
                && settlementReserve.compareTo(leaseDuration) < 0
                && identifier(adapterContract) && identifier(adapterBuild) && identifier(bindingRevision)
                && identifier(activeEndpointBindingKeyVersion)
                && endpointBindingKey(activeEndpointBindingKeyVersion) != null;
    }

    private static boolean within(Duration value, Duration min, Duration max) {
        return value != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }

    private static boolean identifier(String value) {
        return value != null && !value.isBlank() && value.length() <= 128;
    }
}
