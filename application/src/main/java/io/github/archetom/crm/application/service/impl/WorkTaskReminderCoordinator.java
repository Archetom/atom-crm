package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.automation.BackgroundWorkObservability;
import io.github.archetom.crm.application.automation.NotificationDeliveryMetric;
import io.github.archetom.crm.application.automation.NotificationMetric;
import io.github.archetom.crm.application.properties.NotificationReminderProperties;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryOutcomeCounts;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryStore;
import io.github.archetom.crm.application.transaction.AfterCommitExecutor;
import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.entity.WorkTaskSeries;
import io.github.archetom.crm.domain.model.WorkTaskReminderPlan;
import io.github.archetom.crm.domain.repository.NotificationRepository;
import io.github.archetom.crm.domain.repository.WorkTaskReminderRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Keeps task mutation, reminder cancellation/rescheduling and inbox withdrawal in one transaction. */
@Component
@RequiredArgsConstructor
class WorkTaskReminderCoordinator {
    private final WorkTaskReminderRepository reminderRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryStore deliveryStore;
    private final NotificationReminderProperties properties;
    private final AfterCommitExecutor afterCommitExecutor;
    private final BackgroundWorkObservability observability;

    void synchronize(TenantId tenantId, WorkTask task, LocalDateTime now) {
        validateForPersistence(task);
        reminderRepository.synchronizeForTask(tenantId, task, now, properties.safeRetryDeadline());
        int withdrawn = notificationRepository.withdrawStaleForTask(tenantId, task, now);
        NotificationDeliveryOutcomeCounts deliveryOutcomes = deliveryStore.suppressStaleForTask(tenantId, task, now);
        if (withdrawn > 0) {
            afterCommitExecutor.execute("work task reminder cancellation", () ->
                    observability.recordNotificationOutcome(NotificationMetric.WITHDRAWN, withdrawn));
        }
        if (deliveryOutcomes.suppressed() > 0 || deliveryOutcomes.ambiguous() > 0 || deliveryOutcomes.dead() > 0) {
            afterCommitExecutor.execute("work task reminder scheduling", () -> {
                recordDeliveryOutcome(NotificationDeliveryMetric.SUPPRESSED, deliveryOutcomes.suppressed());
                recordDeliveryOutcome(NotificationDeliveryMetric.AMBIGUOUS, deliveryOutcomes.ambiguous());
                recordDeliveryOutcome(NotificationDeliveryMetric.DEAD, deliveryOutcomes.dead());
            });
        }
    }

    void validateForPersistence(WorkTask task) {
        if (task == null) throw new IllegalArgumentException("work task reminder task is required");
        task.getReminderPlan().validateForPersistence(task.getDueTime(), properties.safeRetryDeadline());
    }

    void validateForSeriesTemplate(WorkTaskSeries series, WorkTaskReminderPlan plan) {
        if (series == null || plan == null) {
            throw new IllegalArgumentException("work task reminder series template is required");
        }
        plan.validateForPersistence(series.getNextDueTime(), properties.safeRetryDeadline());
    }

    private void recordDeliveryOutcome(NotificationDeliveryMetric outcome, int count) {
        if (count > 0) observability.recordNotificationDeliveryOutcome(outcome, count);
    }
}
