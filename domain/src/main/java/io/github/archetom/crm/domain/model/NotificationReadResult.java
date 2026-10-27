package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.entity.Notification;

/** Returned under the exclusive inbox-row lock so the first-read fact is observable exactly once. */
public record NotificationReadResult(Notification notification, boolean firstRead) {
    public NotificationReadResult {
        if (notification == null) throw new IllegalArgumentException("notification read result is invalid");
    }
}
