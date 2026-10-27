package io.github.archetom.crm.infra.external;

import io.github.archetom.crm.application.port.out.notification.NotificationChannel;
import io.github.archetom.crm.application.port.out.notification.NotificationChannelAdapter;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchCommand;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchGuarantee;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchResult;
import io.github.archetom.crm.application.port.out.notification.NotificationReadinessSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Deterministic dev/test conformance adapter: it performs no network or mailbox side effect. */
@Component
@Profile("(dev | test) & !prod")
@ConditionalOnProperty(prefix = "atom.notifications.channels.email.synthetic", name = "enabled", havingValue = "true")
public final class SyntheticEmailNotificationChannelAdapter implements NotificationChannelAdapter {
    @Override public NotificationChannel channel() { return NotificationChannel.EMAIL; }

    @Override
    public NotificationReadinessSnapshot readiness() {
        return new NotificationReadinessSnapshot(NotificationChannel.EMAIL, "SYNTHETIC_B27C", "TEST_ONLY",
                "SYNTHETIC_BINDING_V1", NotificationDispatchGuarantee.idempotent(java.time.Duration.ofMinutes(5)),
                "B27C_EMAIL_V1", LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(5));
    }

    @Override
    public NotificationReadinessSnapshot readiness(String templateRevision) {
        if (!"B27C_EMAIL_V1".equals(templateRevision)
                && !"B28B_OA_ANNOUNCEMENT_EMAIL_V1".equals(templateRevision)) {
            return readiness();
        }
        return new NotificationReadinessSnapshot(NotificationChannel.EMAIL, "SYNTHETIC_B27C", "TEST_ONLY",
                "SYNTHETIC_BINDING_V1", NotificationDispatchGuarantee.idempotent(java.time.Duration.ofMinutes(5)),
                templateRevision, LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(5));
    }

    @Override
    public NotificationDispatchResult dispatch(NotificationDispatchCommand command, java.time.Duration deadline) {
        return NotificationDispatchResult.accepted();
    }
}
