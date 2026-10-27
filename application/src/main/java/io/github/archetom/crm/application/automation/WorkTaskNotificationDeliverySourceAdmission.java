package io.github.archetom.crm.application.automation;

import io.github.archetom.crm.application.port.out.notification.NotificationChannelAdapter;
import io.github.archetom.crm.application.port.out.notification.NotificationDelivery;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryAdmission;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliverySourceAdmission;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliverySourceKind;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryTemplate;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.domain.entity.Notification;
import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.model.NotificationStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.NotificationRepository;
import io.github.archetom.crm.domain.repository.WorkTaskRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/** Preserves the B27c task -> notification -> delivery lock order and exact command semantics. */
@Component
class WorkTaskNotificationDeliverySourceAdmission implements NotificationDeliverySourceAdmission {
    private final NotificationDeliveryAdmissionSupport support;
    private final NotificationRepository notificationRepository;
    private final WorkTaskRepository taskRepository;
    private final TenantAuthorizationEvaluator authorizationEvaluator;

    WorkTaskNotificationDeliverySourceAdmission(NotificationDeliveryAdmissionSupport support,
                                                NotificationRepository notificationRepository,
                                                WorkTaskRepository taskRepository,
                                                TenantAuthorizationEvaluator authorizationEvaluator) {
        this.support = support;
        this.notificationRepository = notificationRepository;
        this.taskRepository = taskRepository;
        this.authorizationEvaluator = authorizationEvaluator;
    }

    @Override public NotificationDeliverySourceKind sourceKind() {
        return NotificationDeliverySourceKind.WORK_TASK_NOTIFICATION;
    }

    @Override
    public NotificationDeliveryAdmission admit(NotificationDelivery claim, NotificationChannelAdapter adapter,
                                                UUID leaseOwner, LocalDateTime now) {
        Optional<WorkTask> task = taskRepository.findByIdForReminderEffect(claim.tenantId(), claim.taskId());
        Optional<Notification> notification = notificationRepository.findByIdForDeliveryAdmission(
                claim.tenantId(), claim.notificationId());
        Optional<NotificationDelivery> locked = support.lockClaim(claim, leaseOwner, now);
        if (task.isEmpty() || notification.isEmpty() || locked.isEmpty()) return NotificationDeliveryAdmission.none(claim);
        NotificationDelivery delivery = locked.get();
        if (!matches(delivery, notification.get(), task.get())) {
            return support.suppress(delivery, leaseOwner, "STALE_NOTIFICATION", now);
        }
        if (!support.workEmailEnabled(delivery)) return support.suppress(delivery, leaseOwner, "PREFERENCE_OFF", now);
        if (!authorized(delivery)) return support.suppress(delivery, leaseOwner, "ADMISSION_REVOKED", now);
        return support.begin(delivery, adapter, leaseOwner, now, new NotificationDeliveryDispatchContent(
                sourceKind(), notification.get().getId().getValue(), NotificationDeliveryTemplate.EMAIL_TEMPLATE_REVISION,
                NotificationDeliveryTemplate.payloadDigest(notification.get()), "b27c:" + delivery.id() + ":"
                + delivery.payloadDigest(), "B27C_EMAIL_ENDPOINT_BINDING_V1", notification.get().getKind().name(),
                notification.get().getTitle(), notification.get().getPriority().name(), notification.get().getDueTime()));
    }

    private boolean authorized(NotificationDelivery delivery) {
        TenantAuthorizationEvaluator.Evaluation value = authorizationEvaluator.evaluate(
                delivery.tenantId(), delivery.recipientMemberId());
        return value.activeMember() && has(value, AuthorityCatalog.NOTIFICATIONS_READ)
                && has(value, AuthorityCatalog.WORK_TASKS_READ);
    }

    private static boolean matches(NotificationDelivery delivery, Notification notification, WorkTask task) {
        return notification.getStatus() != NotificationStatus.WITHDRAWN && task.getStatus().isOpen()
                && task.getNotificationProjectionVersion() == notification.getTaskNotificationProjectionVersion()
                && delivery.recipientMemberId().sameValueAs(notification.getRecipientMemberId())
                && delivery.taskId().sameValueAs(notification.getTaskId()) && (task.isCreatedBy(delivery.recipientMemberId())
                || task.isAssignedTo(delivery.recipientMemberId())
                || task.getFollowerMemberIds().contains(delivery.recipientMemberId()));
    }

    private static boolean has(TenantAuthorizationEvaluator.Evaluation evaluation, String authority) {
        return evaluation.effectiveGrants().stream()
                .anyMatch(grant -> authority.equals(grant.authorityCode().getValue()));
    }
}
