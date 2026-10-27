package io.github.archetom.crm.infra.external;

import io.github.archetom.crm.application.port.out.UserNotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Non-production notification adapter that records intent without claiming delivery.
 *
 * <p>Configure {@link SmtpUserNotificationAdapter} or provide another delivery adapter before
 * enabling the {@code prod} profile. Its absence in production deliberately makes missing
 * notification infrastructure fail fast during startup.</p>
 */
@Slf4j
@Component
@Profile("!prod")
public class LoggingUserNotificationAdapter implements UserNotificationPort {

    @Override
    public void sendWelcomeNotification(String email, String username) {
        log.info("Welcome notification not delivered; logging adapter is active");
    }
}
