package io.github.archetom.crm.application.port.out.notification;

import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.NotificationId;
import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;

import java.time.LocalDateTime;
import java.util.UUID;

/** Application-owned durable external-effect ledger row. It contains no endpoint or provider response. */
public record NotificationDelivery(
        Long id,
        TenantId tenantId,
        NotificationDeliverySourceKind sourceKind,
        NotificationId notificationId,
        MemberId recipientMemberId,
        WorkTaskId taskId,
        OaAnnouncementId announcementId,
        NotificationChannel channel,
        String templateRevision,
        String payloadDigest,
        String endpointDigest,
        String endpointDigestVersion,
        NotificationDeliveryStatus status,
        int attemptCount,
        LocalDateTime availableAt,
        LocalDateTime deadlineAt,
        UUID leaseOwner,
        long leaseFence,
        LocalDateTime leaseUntil,
        NotificationDispatchGuaranteeKind guaranteeKind,
        NotificationDeliveryEffectCertainty effectCertainty,
        LocalDateTime idempotencyUntil,
        LocalDateTime dispatchStartedTime,
        LocalDateTime terminalTime,
        String safeReason) {

    public NotificationDelivery {
        boolean workSource = sourceKind == NotificationDeliverySourceKind.WORK_TASK_NOTIFICATION;
        boolean announcementSource = sourceKind == NotificationDeliverySourceKind.OA_ANNOUNCEMENT_RECIPIENT;
        if (id != null && id < 1 || tenantId == null || sourceKind == null || recipientMemberId == null
                || (workSource && (notificationId == null || taskId == null || announcementId != null))
                || (announcementSource && (notificationId != null || taskId != null || announcementId == null))
                || channel == null || templateRevision == null || templateRevision.isBlank()
                || !payloadDigest.matches("[0-9a-f]{64}")
                || (endpointDigest != null && !endpointDigest.matches("[0-9a-f]{64}"))
                || ((endpointDigest == null) != (endpointDigestVersion == null))
                || (endpointDigestVersion != null && (endpointDigestVersion.isBlank()
                || endpointDigestVersion.length() > 64))
                || status == null || attemptCount < 0
                || availableAt == null || deadlineAt == null || !deadlineAt.isAfter(availableAt)
                || leaseFence < 0 || effectCertainty == null
                || (safeReason != null && (safeReason.isBlank() || safeReason.length() > 40))) {
            throw new IllegalArgumentException("notification delivery is invalid");
        }
    }

    /** B27c source shape retained for callers/tests that create a Work delivery directly. */
    public NotificationDelivery(Long id, TenantId tenantId, NotificationId notificationId,
                                MemberId recipientMemberId, WorkTaskId taskId,
                                NotificationChannel channel, String templateRevision, String payloadDigest,
                                String endpointDigest, String endpointDigestVersion,
                                NotificationDeliveryStatus status, int attemptCount,
                                LocalDateTime availableAt, LocalDateTime deadlineAt, UUID leaseOwner,
                                long leaseFence, LocalDateTime leaseUntil,
                                NotificationDispatchGuaranteeKind guaranteeKind,
                                NotificationDeliveryEffectCertainty effectCertainty,
                                LocalDateTime idempotencyUntil, LocalDateTime dispatchStartedTime,
                                LocalDateTime terminalTime, String safeReason) {
        this(id, tenantId, NotificationDeliverySourceKind.WORK_TASK_NOTIFICATION, notificationId,
                recipientMemberId, taskId, null, channel, templateRevision, payloadDigest, endpointDigest,
                endpointDigestVersion, status, attemptCount, availableAt, deadlineAt, leaseOwner, leaseFence,
                leaseUntil, guaranteeKind, effectCertainty, idempotencyUntil, dispatchStartedTime,
                terminalTime, safeReason);
    }
}
