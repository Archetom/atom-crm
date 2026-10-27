package io.github.archetom.crm.application.automation;

/** Fixed inbox transition counters; labels never carry a tenant or recipient. */
public enum NotificationMetric {
    UNREAD,
    READ,
    WITHDRAWN
}
