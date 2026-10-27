package io.github.archetom.crm;

import io.github.archetom.crm.application.automation.AutomationKind;
import io.github.archetom.crm.application.automation.AutomationRunMetric;
import io.github.archetom.crm.application.automation.AutomationRunStateCount;
import io.github.archetom.crm.application.automation.AutomationRunStatus;
import io.github.archetom.crm.application.automation.AutomationScheduleStateCount;
import io.github.archetom.crm.application.automation.AutomationScheduleStatus;
import io.github.archetom.crm.application.automation.AutomationSchedulingSnapshot;
import io.github.archetom.crm.application.automation.BackgroundWorkLane;
import io.github.archetom.crm.application.automation.BackgroundWorkPulseOutcome;
import io.github.archetom.crm.application.automation.NotificationMetric;
import io.github.archetom.crm.application.automation.WorkTaskReminderMetric;
import io.github.archetom.crm.application.automation.WorkRecurrenceOutcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MicrometerBackgroundWorkObservabilityTest {
    @Test
    void publishesOnlyFixedLaneAndKindDimensions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerBackgroundWorkObservability observability = new MicrometerBackgroundWorkObservability(registry);

        observability.recordPulse(BackgroundWorkLane.AUTOMATION, BackgroundWorkPulseOutcome.SUBMITTED);
        observability.recordPulseDuration(BackgroundWorkLane.AUTOMATION, Duration.ofMillis(5));
        observability.recordLaneState(BackgroundWorkLane.AUTOMATION, 1, 2);
        observability.recordWorkTaskReminderOutcome(WorkTaskReminderMetric.CLAIMED, 1);
        observability.recordNotificationOutcome(NotificationMetric.UNREAD, 1);
        observability.recordAutomationTransition(AutomationKind.CUSTOMER_POOL_RELEASE,
                AutomationRunMetric.MATERIALIZED, 1);
        observability.recordAutomationTransition(AutomationKind.CUSTOMER_POOL_RELEASE,
                AutomationRunMetric.SUCCEEDED, 1);
        observability.recordAutomationDueLag(AutomationKind.CUSTOMER_POOL_RELEASE, Duration.ofSeconds(2));
        observability.recordAutomationAffected(AutomationKind.CUSTOMER_POOL_RELEASE, 3);
        observability.recordWorkRecurrenceOutcome(WorkRecurrenceOutcome.SKIPPED, 2);
        observability.recordAutomationSnapshot(new AutomationSchedulingSnapshot(
                List.of(new AutomationRunStateCount(AutomationKind.CUSTOMER_POOL_RELEASE,
                        AutomationRunStatus.RETRY_WAIT, 2)),
                List.of(new AutomationScheduleStateCount(AutomationKind.CUSTOMER_POOL_RELEASE,
                        AutomationScheduleStatus.ACTIVE, 1)),
                Map.of(AutomationKind.CUSTOMER_POOL_RELEASE, LocalDateTime.of(2026, 7, 21, 12, 0))));

        assertEquals(1D, registry.get("atom.scheduling.pulses")
                .tag("lane", "automation").tag("outcome", "submitted").counter().count());
        assertEquals(1D, registry.get("atom.scheduling.automation.runs")
                .tag("kind", "customer_pool_release").tag("outcome", "succeeded").counter().count());
        assertEquals(1D, registry.get("atom.scheduling.automation.materialized")
                .tag("kind", "customer_pool_release").counter().count());
        assertEquals(1D, registry.get("atom.work.task.reminders")
                .tag("outcome", "claimed").counter().count());
        assertEquals(1D, registry.get("atom.notifications")
                .tag("outcome", "unread").counter().count());
        assertEquals(3D, registry.get("atom.scheduling.automation.affected")
                .tag("kind", "customer_pool_release").summary().totalAmount());
        assertEquals(2D, registry.get("atom.work.recurrence.outcomes")
                .tag("outcome", "skipped").counter().count());
        assertEquals(2D, registry.get("atom.scheduling.automation.run.state")
                .tag("kind", "customer_pool_release").tag("status", "retry_wait").gauge().value());
        assertEquals(1D, registry.get("atom.scheduling.lane.active")
                .tag("lane", "automation").gauge().value());
    }
}
