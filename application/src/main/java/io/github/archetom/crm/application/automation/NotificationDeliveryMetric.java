package io.github.archetom.crm.application.automation;

/** Fixed delivery outcome labels; no tenant, endpoint, provider, or exception data is observable. */
public enum NotificationDeliveryMetric {
    CLAIMED,
    ACCEPTED,
    REJECTED,
    SUPPRESSED,
    AMBIGUOUS,
    DEAD,
    RETRY_WAIT
}
