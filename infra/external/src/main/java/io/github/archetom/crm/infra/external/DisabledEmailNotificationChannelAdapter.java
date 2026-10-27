package io.github.archetom.crm.infra.external;

import io.github.archetom.crm.application.port.out.notification.NotificationChannel;
import io.github.archetom.crm.application.port.out.notification.NotificationChannelAdapter;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchCommand;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchGuarantee;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchResult;
import io.github.archetom.crm.application.port.out.notification.NotificationReadinessSnapshot;
import java.time.LocalDateTime;

/**
 * Production-safe default. It has no account, endpoint, credential, or send
 * capability, and deliberately cannot turn the sample logging adapter into a
 * reliable provider.
 */
public class DisabledEmailNotificationChannelAdapter implements NotificationChannelAdapter {
    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public NotificationReadinessSnapshot readiness() {
        return new NotificationReadinessSnapshot(NotificationChannel.EMAIL, "DISABLED", "NONE", "NONE",
                NotificationDispatchGuarantee.unsupported(), "B27C_EMAIL_V1",
                LocalDateTime.of(1000, 1, 1, 0, 0));
    }

    @Override
    public NotificationDispatchResult dispatch(NotificationDispatchCommand command, java.time.Duration deadline) {
        return NotificationDispatchResult.rejected();
    }
}
