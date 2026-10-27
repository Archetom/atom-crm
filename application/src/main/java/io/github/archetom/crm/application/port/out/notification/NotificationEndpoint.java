package io.github.archetom.crm.application.port.out.notification;

/** Short-lived adapter-only endpoint. It must not be persisted, logged, or returned by public APIs. */
public record NotificationEndpoint(NotificationChannel channel, String value) {
    public NotificationEndpoint {
        if (channel != NotificationChannel.EMAIL || value == null || value.isBlank() || value.length() > 254) {
            throw new IllegalArgumentException("notification endpoint is invalid");
        }
        value = value.strip();
    }
}
