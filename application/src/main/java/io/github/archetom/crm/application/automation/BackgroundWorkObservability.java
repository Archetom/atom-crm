package io.github.archetom.crm.application.automation;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Application-owned safe observation seam. Implementations must not attach
 * tenant, run, pool, record, actor, or exception values to observations.
 */
public interface BackgroundWorkObservability {
    void recordPulse(BackgroundWorkLane lane, BackgroundWorkPulseOutcome outcome);

    void recordPulseDuration(BackgroundWorkLane lane, Duration duration);

    void recordLaneState(BackgroundWorkLane lane, int active, int queued);

    void recordLaneSuccess(BackgroundWorkLane lane, LocalDateTime utcTime);

    void recordWorkTaskReminderOutcome(WorkTaskReminderMetric outcome, long count);

    void recordOaAnnouncementPublicationOutcome(OaAnnouncementPublicationMetric outcome, long count);

    void recordNotificationOutcome(NotificationMetric outcome, long count);

    void recordNotificationDeliveryOutcome(NotificationDeliveryMetric outcome, long count);

    void recordAutomationTransition(AutomationKind kind, AutomationRunMetric outcome, long count);

    void recordAutomationDueLag(AutomationKind kind, Duration duration);

    void recordAutomationAffected(AutomationKind kind, int affectedCount);

    void recordWorkRecurrenceOutcome(WorkRecurrenceOutcome outcome, long count);

    void recordAutomationSuccess(AutomationKind kind, LocalDateTime utcTime);

    void recordAutomationSnapshot(AutomationSchedulingSnapshot snapshot);
}
