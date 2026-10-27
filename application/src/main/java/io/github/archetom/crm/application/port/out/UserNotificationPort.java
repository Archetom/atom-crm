package io.github.archetom.crm.application.port.out;

/**
 * Outbound port for user-facing notifications triggered by application workflows.
 */
public interface UserNotificationPort {

    /**
     * Sends the welcome notification for a newly created user.
     */
    void sendWelcomeNotification(String email, String username);
}
