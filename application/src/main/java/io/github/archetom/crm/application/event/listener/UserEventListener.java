package io.github.archetom.crm.application.event.listener;

import io.github.archetom.crm.domain.event.UserCreatedEvent;
import io.github.archetom.crm.domain.event.UserStatusChangedEvent;
import io.github.archetom.crm.application.port.out.UserNotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Handles post-commit user events and delegates external effects through ports. */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventListener {

    private final UserNotificationPort notificationPort;

    /** Sends a welcome notification after user creation has committed. */
    @Async
    @EventListener
    public void handleUserCreated(UserCreatedEvent event) {
        log.info("Processing user-created event: userId={}", event.getUserId());

        try {
            notificationPort.sendWelcomeNotification(event.getEmail(), event.getUsername());
            log.info("User-created event processed: userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("User-created event failed: userId={}, exceptionType={}",
                    event.getUserId(), e.getClass().getName());
        }
    }

    /** Records status transitions; add a dedicated outbound port when delivery is required. */
    @Async
    @EventListener
    public void handleUserStatusChanged(UserStatusChangedEvent event) {
        log.info("Processing user-status event: userId={}, oldStatus={}, newStatus={}",
                event.getUserId(), event.getOldStatus(), event.getNewStatus());

        try {
            // based on status send notification
            switch (event.getNewStatus()) {
                case LOCKED -> {
                    log.info("User locked: userId={}", event.getUserId());
                }
                case ACTIVE -> {
                    log.info("User activated: userId={}", event.getUserId());
                }
                case INACTIVE -> {
                    log.info("User deactivated: userId={}", event.getUserId());
                }
                case DELETED -> {
                    log.info("User deleted: userId={}", event.getUserId());
                }
            }

            log.info("User-status event processed: userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("User-status event failed: userId={}, exceptionType={}",
                    event.getUserId(), e.getClass().getName());
        }
    }
}
