package io.github.archetom.crm.application.port.out.notification;

import io.github.archetom.crm.domain.entity.Notification;
import io.github.archetom.crm.domain.entity.OaAnnouncement;
import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** MySQL-backed delivery outbox capability. Every operation is tenant-scoped after an exact row claim. */
public interface NotificationDeliveryStore {
    void createEmailForNotification(TenantId tenantId, Notification notification, LocalDateTime now,
                                    Duration retryDeadline);

    void createEmailForOaAnnouncementRecipients(TenantId tenantId, OaAnnouncement announcement,
                                                 Collection<io.github.archetom.crm.domain.valueobject.MemberId> recipients,
                                                 LocalDateTime now, Duration retryDeadline);

    /** Claims only rows whose fixed template has current adapter readiness evidence. */
    NotificationDeliveryClaim claimDue(Collection<String> supportedTemplateRevisions, UUID leaseOwner,
                                       LocalDateTime now, Duration leaseDuration, int maxAttempts);

    /** Compatibility path for source-agnostic maintenance/integration fixtures. */
    default NotificationDeliveryClaim claimDue(UUID leaseOwner, LocalDateTime now,
                                               Duration leaseDuration, int maxAttempts) {
        return claimDue(NotificationDeliveryTemplate.emailTemplateRevisions(), leaseOwner, now, leaseDuration,
                maxAttempts);
    }

    Optional<NotificationDelivery> findClaimedForUpdate(TenantId tenantId, long deliveryId,
                                                         UUID leaseOwner, long leaseFence, LocalDateTime now);

    boolean beginDispatch(TenantId tenantId, long deliveryId, UUID leaseOwner, long leaseFence,
                          NotificationDispatchGuarantee guarantee, LocalDateTime idempotencyUntil, String endpointDigest,
                          String endpointDigestVersion,
                          LocalDateTime now);

    Optional<NotificationDeliveryStatus> settleDispatch(TenantId tenantId, long deliveryId,
                                                         UUID leaseOwner, long leaseFence,
                                                         NotificationDispatchOutcome outcome, LocalDateTime now,
                                                         int maxAttempts, Duration retryBackoff);

    Optional<NotificationDeliveryStatus> recoverNoDispatchFailure(TenantId tenantId, long deliveryId,
                                                                   UUID leaseOwner, long leaseFence,
                                                                   LocalDateTime now, int maxAttempts,
                                                                   Duration retryBackoff);

    NotificationDeliveryStatus suppressClaim(TenantId tenantId, long deliveryId, UUID leaseOwner, long leaseFence,
                                             String safeReason, LocalDateTime now);

    NotificationDeliveryOutcomeCounts suppressStaleForTask(TenantId tenantId, WorkTask task, LocalDateTime now);

    NotificationDeliveryOutcomeCounts suppressForOaAnnouncement(TenantId tenantId,
            io.github.archetom.crm.domain.valueobject.OaAnnouncementId announcementId, LocalDateTime now);

    LocalDateTime currentUtc();
    NotificationDeliveryOutcomeCounts expireOverdue(LocalDateTime now, int limit);
    void purgeTerminal(LocalDateTime beforeExclusive, int limit);
}
