package io.github.archetom.crm;

import io.github.archetom.crm.application.automation.AutomationPolicy;
import io.github.archetom.crm.application.automation.AutomationSchedulingReadiness;
import io.github.archetom.crm.application.automation.WorkRecurrencePolicy;
import io.github.archetom.crm.application.automation.WorkRecurrenceReadiness;
import io.github.archetom.crm.application.port.out.automation.AutomationScheduleStore;
import io.github.archetom.crm.application.port.out.automation.WorkRecurrenceControlStore;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.properties.WorkRecurrenceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Exposes B26 and B27a control-plane readiness as actuator health. */
@Component("automationScheduling")
@RequiredArgsConstructor
public class AutomationSchedulingHealthIndicator implements HealthIndicator {
    private final SchedulingProperties properties;
    private final AutomationScheduleStore store;
    private final WorkRecurrenceProperties recurrenceProperties;
    private final WorkRecurrenceControlStore recurrenceControl;

    @Override
    public Health health() {
        AutomationPolicy policy = properties.policy();
        if (!policy.enabled()) {
            return Health.up().withDetail("state", "DISABLED").build();
        }
        if (!policy.valid()) {
            return Health.down().withDetail("state", "INVALID_CONFIGURATION").build();
        }
        try {
            AutomationSchedulingReadiness readiness = store.schedulingReadiness(policy);
            if (readiness != AutomationSchedulingReadiness.READY) {
                return Health.down().withDetail("state", "NOT_READY")
                        .withDetail("reason", readiness.name()).build();
            }
            return recurrenceHealth();
        } catch (RuntimeException exception) {
            return Health.down().withDetail("state", "NOT_READY")
                    .withDetail("reason", "CONTROL_PLANE_UNAVAILABLE").build();
        }
    }

    private Health recurrenceHealth() {
        try {
            WorkRecurrencePolicy recurrencePolicy = recurrenceProperties.policy();
            if (!recurrencePolicy.enabled()) {
                return Health.up().withDetail("state", "RECURRENCE_DISABLED")
                        .withDetail("recurrenceState", "DISABLED").build();
            }
            if (!properties.recurrenceValid()) {
                return Health.down().withDetail("state", "RECURRENCE_NOT_READY")
                        .withDetail("recurrenceState", "CONFIG_INVALID").build();
            }
            WorkRecurrenceReadiness readiness = recurrenceControl.readiness(recurrencePolicy);
            if (readiness == WorkRecurrenceReadiness.READY) {
                return Health.up().withDetail("state", "READY")
                        .withDetail("recurrenceState", "READY").build();
            }
            if (readiness == WorkRecurrenceReadiness.DISABLED) {
                return Health.up().withDetail("state", "RECURRENCE_DISABLED")
                        .withDetail("recurrenceState", "DISABLED").build();
            }
            return Health.down().withDetail("state", "RECURRENCE_NOT_READY")
                    .withDetail("recurrenceState", readiness.name()).build();
        } catch (RuntimeException exception) {
            return Health.down().withDetail("state", "RECURRENCE_NOT_READY")
                    .withDetail("recurrenceState", "CONTROL_PLANE_UNAVAILABLE").build();
        }
    }
}
