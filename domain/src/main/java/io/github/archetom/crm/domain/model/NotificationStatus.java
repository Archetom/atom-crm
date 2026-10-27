package io.github.archetom.crm.domain.model;

/** Inbox state, intentionally independent from a reminder occurrence or delivery. */
public enum NotificationStatus {
    UNREAD,
    READ,
    WITHDRAWN
}
