package io.github.archetom.crm.application.port.out.notification;

import java.time.LocalDateTime;

/** Fixed EMAIL command. Provider-native request objects and response fields terminate in infra. */
public record NotificationDispatchCommand(
        long deliveryId,
        long notificationId,
        NotificationChannel channel,
        String templateRevision,
        String payloadDigest,
        String idempotencyKey,
        NotificationEndpoint endpoint,
        String kind,
        String title,
        String priority,
        LocalDateTime dueTime) {

    public NotificationDispatchCommand {
        if (deliveryId < 1 || notificationId < 1 || channel != NotificationChannel.EMAIL
                || blank(templateRevision, 64) || !payloadDigest.matches("[0-9a-f]{64}")
                || blank(idempotencyKey, 160) || endpoint == null || blank(kind, 32)
                || invalidTitle(title) || blank(priority, 20) || dueTime == null) {
            throw new IllegalArgumentException("notification dispatch command is invalid");
        }
    }

    private static boolean blank(String value, int max) {
        return value == null || value.isBlank() || value.length() > max;
    }

    private static boolean invalidTitle(String value) {
        return value == null || value.isBlank() || value.codePointCount(0, value.length()) > 200;
    }
}
