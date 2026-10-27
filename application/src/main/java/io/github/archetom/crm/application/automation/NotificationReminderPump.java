package io.github.archetom.crm.application.automation;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.properties.NotificationReminderProperties;
import io.github.archetom.crm.application.properties.NotificationDeliveryProperties;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryStore;
import io.github.archetom.crm.application.port.out.notification.NotificationPreferenceStore;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.OperationLogPolicy;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.entity.Notification;
import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.model.WorkTaskReminderClaim;
import io.github.archetom.crm.domain.model.WorkTaskReminderOccurrence;
import io.github.archetom.crm.domain.model.WorkTaskReminderRecipient;
import io.github.archetom.crm.domain.model.WorkTaskReminderOccurrenceStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.NotificationRepository;
import io.github.archetom.crm.domain.repository.WorkTaskReminderRepository;
import io.github.archetom.crm.domain.repository.WorkTaskRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Claims one exact reminder occurrence; no external channel or B26 automation run is involved. */
@Component
@RequiredArgsConstructor
public class NotificationReminderPump implements BackgroundWorkPump {
    private final WorkTaskReminderRepository reminderRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryStore deliveryStore;
    private final NotificationPreferenceStore preferenceStore;
    private final NotificationDeliveryProperties deliveryProperties;
    private final WorkTaskRepository taskRepository;
    private final TenantAuthorizationEvaluator authorizationEvaluator;
    private final NotificationReminderProperties properties;
    private final CommandServiceTemplate commandTemplate;
    private final BackgroundWorkObservability observability;
    private final UUID leaseOwner = UUID.randomUUID();

    @Override
    public void runOnce() {
        if (!properties.isEnabled() || !properties.valid()) return;
        purgeExpiredNotifications();
        purgeTerminalOccurrences();
        Result<WorkTaskReminderClaim> claimed = commandTemplate.execute(
                UseCaseOperation.WORK_TASK_REMINDER_EFFECT, new ServiceOperation<>() {
                    @Override public WorkTaskReminderClaim execute() {
                        // Claim admission is based on one MySQL UTC instant, never this JVM's clock.
                        LocalDateTime now = reminderRepository.currentUtc();
                        return reminderRepository.claimDue(leaseOwner, now, properties.getLeaseDuration(),
                                properties.getMaxAttempts());
                    }
                }, OperationLogPolicy.SAFE_BACKGROUND);
        requireSuccess(claimed, "claim");
        WorkTaskReminderClaim claim = claimed.getData();
        if (claim.deadCount() > 0) {
            observability.recordWorkTaskReminderOutcome(WorkTaskReminderMetric.DEAD, claim.deadCount());
        }
        if (claim.occurrence().isEmpty()) return;
        WorkTaskReminderOccurrence occurrence = claim.occurrence().get();
        observability.recordWorkTaskReminderOutcome(WorkTaskReminderMetric.CLAIMED, 1);
        Result<ReminderEffect> effect = commandTemplate.execute(UseCaseOperation.WORK_TASK_REMINDER_EFFECT,
                new ServiceOperation<>() {
                    @Override public ReminderEffect execute() {
                        return emitOrCancel(occurrence, reminderRepository.currentUtc());
                    }
                }, OperationLogPolicy.SAFE_BACKGROUND);
        if (effect == null) {
            throw new IllegalStateException("work task reminder effect did not return a result");
        }
        if (effect.isSuccess()) {
            if (effect.getData() == null) {
                throw new IllegalStateException("work task reminder effect did not return an outcome");
            }
            ReminderEffect completed = effect.getData();
            if (completed.outcome() != null) {
                observability.recordWorkTaskReminderOutcome(completed.outcome(), 1);
            }
            if (completed.unreadCount() > 0) {
                observability.recordNotificationOutcome(NotificationMetric.UNREAD, completed.unreadCount());
            }
            return;
        }
        Result<WorkTaskReminderOccurrenceStatus> recovered = commandTemplate.execute(UseCaseOperation.WORK_TASK_REMINDER_EFFECT,
                new ServiceOperation<>() {
                    @Override public WorkTaskReminderOccurrenceStatus execute() {
                        LocalDateTime now = reminderRepository.currentUtc();
                        return reminderRepository.recoverFailure(occurrence.tenantId(), occurrence.id(), leaseOwner,
                                occurrence.leaseFence(), now, properties.getMaxAttempts(),
                                properties.getRetryBackoff());
                    }
                }, OperationLogPolicy.SAFE_BACKGROUND);
        if (recovered.isSuccess() && recovered.getData() == WorkTaskReminderOccurrenceStatus.DEAD) {
            observability.recordWorkTaskReminderOutcome(WorkTaskReminderMetric.DEAD, 1);
        }
        if (!recovered.isSuccess()) {
            throw new IllegalStateException("work task reminder recovery failed");
        }
    }

    /**
     * Retention and claim deliberately have separate commits. A task mutation owns
     * task -> occurrence -> notification; combining either cleanup with a claim
     * would let the pump invert that order through notification -> occurrence.
     */
    private void purgeExpiredNotifications() {
        Result<Void> result = commandTemplate.execute(UseCaseOperation.WORK_TASK_REMINDER_EFFECT, new ServiceOperation<>() {
            @Override public Void execute() {
                LocalDateTime now = reminderRepository.currentUtc();
                notificationRepository.purgeExpired(now.minus(properties.getNotificationRetention()), 100);
                return null;
            }
        }, OperationLogPolicy.SAFE_BACKGROUND);
        requireSuccess(result, "notification retention");
    }

    private void purgeTerminalOccurrences() {
        Result<Void> result = commandTemplate.execute(UseCaseOperation.WORK_TASK_REMINDER_EFFECT, new ServiceOperation<>() {
            @Override public Void execute() {
                LocalDateTime now = reminderRepository.currentUtc();
                reminderRepository.purgeTerminal(now.minus(properties.getTerminalRetention()), 100);
                return null;
            }
        }, OperationLogPolicy.SAFE_BACKGROUND);
        requireSuccess(result, "occurrence retention");
    }

    private ReminderEffect emitOrCancel(WorkTaskReminderOccurrence claim, LocalDateTime effectTime) {
        // The task root is locked before its occurrence. Task mutations use the same
        // order while reconciling reminder rows, so a stale claimed occurrence cannot
        // race a newer notification projection into the inbox.
        Optional<WorkTask> task = taskRepository.findByIdForReminderEffect(
                claim.tenantId(), claim.taskId());
        Optional<WorkTaskReminderOccurrence> locked = reminderRepository.findClaimedForUpdate(
                claim.tenantId(), claim.id(), leaseOwner, claim.leaseFence(), effectTime);
        if (locked.isEmpty()) return ReminderEffect.none();
        WorkTaskReminderOccurrence occurrence = locked.get();
        if (task.isEmpty() || !eligible(task.get(), occurrence)) {
            reminderRepository.cancelClaim(occurrence.tenantId(), occurrence.id(), leaseOwner,
                    occurrence.leaseFence(), effectTime);
            return new ReminderEffect(WorkTaskReminderMetric.CANCELLED, 0);
        }
        WorkTask current = task.get();
        var step = current.getReminderPlan().requireStep(occurrence.stepKey());
        Map<MemberId, TenantAuthorizationEvaluator.Evaluation> authorizations = authorizationEvaluator.evaluateAll(
                occurrence.tenantId(), recipients(current, step.recipients()));
        int unreadCount = 0;
        for (MemberId recipient : recipients(current, step.recipients())) {
            if (!canReceive(authorizations.get(recipient))) continue;
            Notification candidate = Notification.create(occurrence.tenantId(), recipient,
                    step.notificationKind(), occurrence.id(), current.getId(), current.getNotificationProjectionVersion(),
                    current.getTitle(), current.getPriority(), current.getDueTime(), effectTime);
            if (notificationRepository.save(occurrence.tenantId(), candidate) == candidate) {
                unreadCount++;
                // Creation is in the same reminder-effect transaction as the inbox fact.
                // A default-OFF preference deliberately produces no external outbox row.
                if (deliveryProperties.dispatchEnabled(properties.getNotificationRetention())
                        && preferenceStore.emailEnabled(occurrence.tenantId(), recipient)) {
                    deliveryStore.createEmailForNotification(occurrence.tenantId(), candidate, effectTime,
                            deliveryProperties.safeRetryDeadline());
                }
            }
        }
        reminderRepository.markEmitted(occurrence.tenantId(), occurrence.id(), leaseOwner,
                occurrence.leaseFence(), effectTime);
        return new ReminderEffect(WorkTaskReminderMetric.EMITTED, unreadCount);
    }

    private boolean eligible(WorkTask task, WorkTaskReminderOccurrence occurrence) {
        if (!task.getStatus().isOpen() || task.getDueTime() == null
                || task.getNotificationProjectionVersion() != occurrence.notificationProjectionVersion()) return false;
        try {
            return task.getReminderPlan().requireStep(occurrence.stepKey()).dueAt(task.getDueTime())
                    .equals(occurrence.dueTime());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static LinkedHashSet<MemberId> recipients(
            WorkTask task, java.util.Set<WorkTaskReminderRecipient> roles) {
        LinkedHashSet<MemberId> result = new LinkedHashSet<>();
        if (roles.contains(WorkTaskReminderRecipient.ASSIGNEE)) result.add(task.getCurrentAssigneeMemberId());
        if (roles.contains(WorkTaskReminderRecipient.CREATOR)) result.add(task.getCreatedByMemberId());
        if (roles.contains(WorkTaskReminderRecipient.FOLLOWERS)) result.addAll(task.getFollowerMemberIds());
        return result;
    }

    private static boolean canReceive(TenantAuthorizationEvaluator.Evaluation evaluation) {
        return evaluation != null && evaluation.activeMember()
                && has(evaluation, AuthorityCatalog.NOTIFICATIONS_READ)
                && has(evaluation, AuthorityCatalog.WORK_TASKS_READ);
    }
    private static boolean has(TenantAuthorizationEvaluator.Evaluation evaluation, String authority) {
        return evaluation.effectiveGrants().stream().anyMatch(grant -> authority.equals(grant.authorityCode().getValue()));
    }

    private static <T> void requireSuccess(Result<T> result, String stage) {
        if (result == null || !result.isSuccess()) {
            throw new IllegalStateException("work task reminder " + stage + " failed");
        }
    }

    private record ReminderEffect(WorkTaskReminderMetric outcome, int unreadCount) {
        private static ReminderEffect none() { return new ReminderEffect(null, 0); }
    }
}
