package io.github.archetom.crm.application.automation;

import io.github.archetom.crm.application.port.out.notification.NotificationDeliverySourceKind;

import java.time.LocalDateTime;

/** Closed source-owned EMAIL content. No body, selector, endpoint, or provider fields enter this value. */
record NotificationDeliveryDispatchContent(
        NotificationDeliverySourceKind sourceKind,
        long sourceId,
        String templateRevision,
        String payloadDigest,
        String idempotencyKey,
        String endpointBindingPurpose,
        String kind,
        String title,
        String priority,
        LocalDateTime dueTime) {

    NotificationDeliveryDispatchContent {
        if (sourceKind == null || sourceId < 1 || blank(templateRevision, 64)
                || payloadDigest == null || !payloadDigest.matches("[0-9a-f]{64}")
                || blank(idempotencyKey, 160) || blank(endpointBindingPurpose, 96)
                || blank(kind, 32) || invalidTitle(title) || blank(priority, 20) || dueTime == null) {
            throw new IllegalArgumentException("notification delivery dispatch content is invalid");
        }
    }

    private static boolean blank(String value, int maximum) {
        return value == null || value.isBlank() || value.length() > maximum;
    }

    private static boolean invalidTitle(String value) {
        return value == null || value.isBlank() || value.codePointCount(0, value.length()) > 200;
    }
}
