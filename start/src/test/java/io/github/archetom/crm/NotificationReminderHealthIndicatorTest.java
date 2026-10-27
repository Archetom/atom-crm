package io.github.archetom.crm;

import io.github.archetom.crm.application.properties.NotificationReminderProperties;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationReminderHealthIndicatorTest {

    @Test
    void invalidEnabledReminderPolicyIsDownRatherThanSilentlyDisabled() {
        NotificationReminderProperties properties = new NotificationReminderProperties();
        properties.setPumpConcurrency(0);

        var health = new NotificationReminderHealthIndicator(properties, new SchedulingProperties(),
                new NotificationReminderRuntimeStatus()).health();

        assertFalse(health.getStatus().equals(org.springframework.boot.health.contributor.Status.UP));
        assertEquals("CONFIG_INVALID", health.getDetails().get("state"));
    }

    @Test
    void disabledLaneIsExplicitlyHealthyButNotReadyToRun() {
        NotificationReminderProperties properties = new NotificationReminderProperties();
        properties.setEnabled(false);
        properties.setLeaseDuration(Duration.ZERO);

        var health = new NotificationReminderHealthIndicator(properties, new SchedulingProperties(),
                new NotificationReminderRuntimeStatus()).health();

        assertTrue(health.getStatus().equals(org.springframework.boot.health.contributor.Status.UP));
        assertEquals("DISABLED", health.getDetails().get("state"));
    }

    @Test
    void runtimeFailureKeepsAnEnabledLaneDownUntilTheNextSuccessfulPump() {
        NotificationReminderProperties properties = new NotificationReminderProperties();
        SchedulingProperties schedulingProperties = new SchedulingProperties();
        NotificationReminderRuntimeStatus runtimeStatus = new NotificationReminderRuntimeStatus();
        NotificationReminderHealthIndicator indicator = new NotificationReminderHealthIndicator(
                properties, schedulingProperties, runtimeStatus);

        runtimeStatus.recordFailure();
        assertFalse(indicator.health().getStatus().equals(org.springframework.boot.health.contributor.Status.UP));
        assertEquals("RUNTIME_FAILURE", indicator.health().getDetails().get("state"));

        runtimeStatus.recordSuccess();
        assertTrue(indicator.health().getStatus().equals(org.springframework.boot.health.contributor.Status.UP));
        assertEquals("READY", indicator.health().getDetails().get("state"));
    }

    @Test
    void enabledReminderLaneIsDownWhenAllSchedulerPulsesAreDisabled() {
        NotificationReminderProperties properties = new NotificationReminderProperties();
        SchedulingProperties schedulingProperties = new SchedulingProperties();
        schedulingProperties.setPulsesEnabled(false);

        var health = new NotificationReminderHealthIndicator(properties, schedulingProperties,
                new NotificationReminderRuntimeStatus()).health();

        assertFalse(health.getStatus().equals(org.springframework.boot.health.contributor.Status.UP));
        assertEquals("SCHEDULER_DISABLED", health.getDetails().get("state"));
    }
}
