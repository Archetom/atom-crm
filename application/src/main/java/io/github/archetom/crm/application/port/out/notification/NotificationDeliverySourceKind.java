package io.github.archetom.crm.application.port.out.notification;

/**
 * Closed parent-fact catalog for the external delivery ledger. New sources
 * require a schema proof and an explicit admission implementation.
 */
public enum NotificationDeliverySourceKind {
    WORK_TASK_NOTIFICATION,
    OA_ANNOUNCEMENT_RECIPIENT
}
