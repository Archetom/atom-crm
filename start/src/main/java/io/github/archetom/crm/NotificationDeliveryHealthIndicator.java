package io.github.archetom.crm;

import io.github.archetom.crm.application.properties.NotificationDeliveryProperties;
import io.github.archetom.crm.application.properties.NotificationReminderProperties;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** B27c channel-lane health; it never claims that a provider is ready or that email arrived. */
@Component("notificationDelivery")
@RequiredArgsConstructor
public class NotificationDeliveryHealthIndicator implements HealthIndicator {
    private final NotificationDeliveryProperties properties;
    private final NotificationReminderProperties reminderProperties;
    private final SchedulingProperties schedulingProperties;
    private final NotificationDeliveryRuntimeStatus runtimeStatus;

    @Override
    public Health health() {
        if (!properties.isEnabled()) return Health.up().withDetail("state", "DISABLED").build();
        if (!properties.valid() || !properties.dispatchEnabled(reminderProperties.getNotificationRetention())) {
            return Health.down().withDetail("state", "CONFIG_INVALID").build();
        }
        if (!schedulingProperties.isPulsesEnabled()) {
            return Health.down().withDetail("state", "SCHEDULER_DISABLED").build();
        }
        if (runtimeStatus.failedSinceLastSuccess()) {
            return Health.down().withDetail("state", "RUNTIME_FAILURE").build();
        }
        return Health.up().withDetail("state", "READY").build();
    }
}
