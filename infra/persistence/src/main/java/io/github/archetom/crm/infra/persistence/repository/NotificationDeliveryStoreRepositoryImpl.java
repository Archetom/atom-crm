package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.application.port.out.notification.NotificationChannel;
import io.github.archetom.crm.application.port.out.notification.NotificationDelivery;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryClaim;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryEffectCertainty;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryOutcomeCounts;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryStatus;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliverySourceKind;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryStore;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryTemplate;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchGuarantee;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchGuaranteeKind;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchOutcome;
import io.github.archetom.crm.domain.entity.Notification;
import io.github.archetom.crm.domain.entity.OaAnnouncement;
import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.NotificationId;
import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.NotificationDeliveryMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.NotificationDeliveryPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

/** MySQL implementation of the B27c outbox, lease, and recovery protocol. */
@Repository
@RequiredArgsConstructor
public class NotificationDeliveryStoreRepositoryImpl implements NotificationDeliveryStore {
    private static final int CLAIM_RECOVERY_LIMIT = 10;
    private static final int INSERT_CHUNK = 500;
    private final NotificationDeliveryMapper mapper;

    @Override
    public void createEmailForNotification(TenantId tenantId, Notification notification, LocalDateTime now,
                                           Duration retryDeadline) {
        if (tenantId == null || notification == null || notification.getId() == null || now == null
                || retryDeadline == null || retryDeadline.isNegative() || retryDeadline.isZero()
                || notification.getTenantId() == null || !tenantId.sameValueAs(notification.getTenantId())) {
            throw new IllegalArgumentException("notification delivery creation is invalid");
        }
        NotificationDeliveryPO row = new NotificationDeliveryPO();
        row.setTenantId(tenantId.getValue()); row.setSourceKind(NotificationDeliverySourceKind.WORK_TASK_NOTIFICATION.name());
        row.setNotificationId(notification.getId().getValue());
        row.setRecipientActorId(notification.getRecipientMemberId().getValue());
        row.setWorkTaskId(notification.getTaskId().getValue()); row.setChannel(NotificationChannel.EMAIL.name());
        row.setTemplateRevision(NotificationDeliveryTemplate.EMAIL_TEMPLATE_REVISION);
        row.setPayloadDigest(NotificationDeliveryTemplate.payloadDigest(notification));
        row.setStatus(NotificationDeliveryStatus.READY.name()); row.setAttemptCount(0);
        row.setAvailableAt(now); row.setDeadlineAt(now.plus(retryDeadline)); row.setLeaseFence(0L);
        try {
            if (mapper.insert(row) != 1) throw new IllegalStateException("notification delivery insert failed");
        } catch (DuplicateKeyException ignored) {
            // The notification identity is already durable; exact uniqueness remains the crash-recovery truth.
        }
    }

    @Override
    public void createEmailForOaAnnouncementRecipients(TenantId tenantId, OaAnnouncement announcement,
                                                        Collection<MemberId> recipients, LocalDateTime now,
                                                        Duration retryDeadline) {
        if (tenantId == null || announcement == null || announcement.getId() == null
                || announcement.getPublishedTime() == null || recipients == null || recipients.isEmpty()
                || now == null || retryDeadline == null || retryDeadline.isNegative() || retryDeadline.isZero()
                || announcement.getTenantId() == null || !tenantId.sameValueAs(announcement.getTenantId())) {
            throw new IllegalArgumentException("OA announcement delivery creation is invalid");
        }
        if (recipients.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("OA announcement delivery recipients are invalid");
        }
        java.util.List<Long> recipientIds = recipients.stream().map(MemberId::getValue).distinct().toList();
        if (recipientIds.isEmpty() || recipientIds.size() != recipients.size()) {
            throw new IllegalArgumentException("OA announcement delivery recipients are invalid");
        }
        String digest = NotificationDeliveryTemplate.oaAnnouncementPayloadDigest(announcement);
        LocalDateTime deadline = now.plus(retryDeadline);
        for (int start = 0; start < recipientIds.size(); start += INSERT_CHUNK) {
            java.util.List<Long> chunk = recipientIds.subList(start, Math.min(start + INSERT_CHUNK, recipientIds.size()));
            if (mapper.insertOaAnnouncementEmailBatch(tenantId.getValue(), announcement.getId().getValue(), chunk,
                    NotificationDeliveryTemplate.OA_ANNOUNCEMENT_EMAIL_TEMPLATE_REVISION, digest, now, deadline)
                    != chunk.size()) {
                throw new IllegalStateException("OA announcement delivery insert count is invalid");
            }
        }
    }

    @Override
    public NotificationDeliveryClaim claimDue(Collection<String> supportedTemplateRevisions, UUID leaseOwner,
                                              LocalDateTime now, Duration leaseDuration, int maxAttempts) {
        java.util.List<String> templateRevisions = supportedTemplateRevisions == null ? java.util.List.of()
                : supportedTemplateRevisions.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (templateRevisions.isEmpty() || !NotificationDeliveryTemplate.emailTemplateRevisions()
                .containsAll(templateRevisions) || leaseOwner == null || now == null
                || invalidDuration(leaseDuration) || !validAttempts(maxAttempts)) {
            throw new IllegalArgumentException("notification delivery claim is invalid");
        }
        int dead = 0;
        int ambiguous = 0;
        for (int index = 0; index < CLAIM_RECOVERY_LIMIT; index++) {
            NotificationDeliveryPO candidate = mapper.selectClaimableForUpdate(now, maxAttempts, templateRevisions);
            if (candidate == null) return new NotificationDeliveryClaim(Optional.empty(), dead, ambiguous);
            NotificationDeliveryStatus status = NotificationDeliveryStatus.valueOf(candidate.getStatus());
            if (status == NotificationDeliveryStatus.READY || status == NotificationDeliveryStatus.RETRY_WAIT) {
                if (candidate.getAttemptCount() >= maxAttempts || !candidate.getDeadlineAt().isAfter(now)) {
                    requireOne(mapper.deadBeforeClaim(candidate.getId(), now, maxAttempts), "delivery exhaustion transition");
                    if (ambiguousEffect(candidate)) ambiguous++; else dead++;
                    continue;
                }
                LocalDateTime leaseUntil = now.plus(leaseDuration);
                if (leaseUntil.isAfter(candidate.getDeadlineAt())) leaseUntil = candidate.getDeadlineAt();
                requireOne(mapper.claimReady(candidate.getId(), leaseOwner.toString(), leaseUntil, now, maxAttempts),
                        "delivery claim");
                return claimed(candidate.getTenantId(), candidate.getId(), dead, ambiguous);
            }
            if (status == NotificationDeliveryStatus.LEASED) {
                if (candidate.getAttemptCount() >= maxAttempts || !candidate.getDeadlineAt().isAfter(now)) {
                    requireOne(mapper.deadBeforeClaim(candidate.getId(), now, maxAttempts),
                            "expired lease exhaustion transition");
                    if (ambiguousEffect(candidate)) ambiguous++; else dead++;
                    continue;
                }
                requireOne(mapper.requeueExpiredLease(candidate.getId(), now), "expired delivery lease transition");
                continue;
            }
            if (status == NotificationDeliveryStatus.DISPATCHING) {
                boolean reusable = NotificationDispatchGuaranteeKind.IDEMPOTENT_SEND.name().equals(candidate.getGuarantee())
                        && candidate.getIdempotencyUntil() != null && candidate.getIdempotencyUntil().isAfter(now)
                        && candidate.getDeadlineAt().isAfter(now) && candidate.getAttemptCount() < maxAttempts;
                if (reusable) {
                    requireOne(mapper.recoverExpiredIdempotentDispatch(candidate.getId(), now, maxAttempts),
                            "idempotent dispatch recovery");
                } else {
                    requireOne(mapper.markExpiredDispatchAmbiguous(candidate.getId(), now),
                            "ambiguous dispatch recovery");
                    ambiguous++;
                }
                continue;
            }
            throw new IllegalStateException("non-claimable notification delivery was selected");
        }
        return new NotificationDeliveryClaim(Optional.empty(), dead, ambiguous);
    }

    @Override
    public Optional<NotificationDelivery> findClaimedForUpdate(TenantId tenantId, long deliveryId,
                                                                 UUID leaseOwner, long leaseFence,
                                                                 LocalDateTime now) {
        if (tenantId == null || deliveryId < 1 || leaseOwner == null || leaseFence < 1 || now == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<NotificationDeliveryPO>()
                .eq(NotificationDeliveryPO::getTenantId, tenantId.getValue())
                .eq(NotificationDeliveryPO::getId, deliveryId)
                .eq(NotificationDeliveryPO::getStatus, NotificationDeliveryStatus.LEASED.name())
                .eq(NotificationDeliveryPO::getLeaseOwner, leaseOwner.toString())
                .eq(NotificationDeliveryPO::getLeaseFence, leaseFence)
                .gt(NotificationDeliveryPO::getLeaseUntil, now)
                .gt(NotificationDeliveryPO::getDeadlineAt, now).last("FOR UPDATE"))).map(this::value);
    }

    @Override
    public boolean beginDispatch(TenantId tenantId, long deliveryId, UUID leaseOwner, long leaseFence,
                                 NotificationDispatchGuarantee guarantee, LocalDateTime idempotencyUntil,
                                 String endpointDigest, String endpointDigestVersion, LocalDateTime now) {
        if (tenantId == null || deliveryId < 1 || leaseOwner == null || leaseFence < 1 || guarantee == null
                || guarantee.kind() == NotificationDispatchGuaranteeKind.UNSUPPORTED || now == null
                || endpointDigest == null || !endpointDigest.matches("[0-9a-f]{64}")
                || endpointDigestVersion == null || endpointDigestVersion.isBlank() || endpointDigestVersion.length() > 64
                || (guarantee.kind() == NotificationDispatchGuaranteeKind.IDEMPOTENT_SEND && idempotencyUntil == null)) {
            throw new IllegalArgumentException("notification delivery dispatch admission is invalid");
        }
        return mapper.beginDispatch(tenantId.getValue(), deliveryId, leaseOwner.toString(), leaseFence,
                guarantee.kind().name(), idempotencyUntil, endpointDigest, endpointDigestVersion, now) == 1;
    }

    @Override
    public Optional<NotificationDeliveryStatus> settleDispatch(TenantId tenantId, long deliveryId,
                                                                UUID leaseOwner, long leaseFence,
                                                                NotificationDispatchOutcome outcome, LocalDateTime now,
                                                                int maxAttempts, Duration retryBackoff) {
        requireTransition(tenantId, deliveryId, leaseOwner, leaseFence, now, maxAttempts, retryBackoff);
        if (outcome == null) throw new IllegalArgumentException("notification dispatch outcome is required");
        int changed;
        if (outcome == NotificationDispatchOutcome.ACCEPTED) {
            changed = mapper.settleTerminal(tenantId.getValue(), deliveryId, leaseOwner.toString(), leaseFence,
                    NotificationDeliveryStatus.ACCEPTED.name(), null, now);
        } else if (outcome == NotificationDispatchOutcome.REJECTED) {
            changed = mapper.settleTerminal(tenantId.getValue(), deliveryId, leaseOwner.toString(), leaseFence,
                    NotificationDeliveryStatus.REJECTED.name(), "PROVIDER_REJECTED", now);
        } else {
            LocalDateTime retryAt = now.plus(retryBackoff);
            NotificationDeliveryEffectCertainty effectCertainty = outcome == NotificationDispatchOutcome.AMBIGUOUS
                    ? NotificationDeliveryEffectCertainty.AMBIGUOUS : NotificationDeliveryEffectCertainty.NO_EFFECT;
            changed = mapper.retryIdempotentOrTerminal(tenantId.getValue(), deliveryId, leaseOwner.toString(),
                    leaseFence, now, maxAttempts, retryAt, effectCertainty.name());
            if (changed == 0) {
                String safeReason = outcome == NotificationDispatchOutcome.AMBIGUOUS
                        ? "PROVIDER_AMBIGUOUS" : "AT_MOST_ONCE_FAILURE";
                changed = mapper.settleAtMostOnceAmbiguous(tenantId.getValue(), deliveryId, leaseOwner.toString(),
                        leaseFence, safeReason, now);
            }
        }
        return changed == 1 ? status(tenantId, deliveryId) : Optional.empty();
    }

    @Override
    public Optional<NotificationDeliveryStatus> recoverNoDispatchFailure(TenantId tenantId, long deliveryId,
                                                                          UUID leaseOwner, long leaseFence,
                                                                          LocalDateTime now, int maxAttempts,
                                                                          Duration retryBackoff) {
        requireTransition(tenantId, deliveryId, leaseOwner, leaseFence, now, maxAttempts, retryBackoff);
        int changed = mapper.retryLeaseOrDead(tenantId.getValue(), deliveryId, leaseOwner.toString(), leaseFence,
                now, maxAttempts, now.plus(retryBackoff));
        return changed == 1 ? status(tenantId, deliveryId) : Optional.empty();
    }

    @Override
    public NotificationDeliveryStatus suppressClaim(TenantId tenantId, long deliveryId, UUID leaseOwner, long leaseFence,
                                                     String safeReason, LocalDateTime now) {
        if (tenantId == null || deliveryId < 1 || leaseOwner == null || leaseFence < 1 || safeReason == null
                || safeReason.isBlank() || safeReason.length() > 40 || now == null) {
            throw new IllegalArgumentException("notification delivery suppression is invalid");
        }
        requireOne(mapper.suppressClaim(tenantId.getValue(), deliveryId, leaseOwner.toString(), leaseFence,
                safeReason, now), "notification delivery suppression");
        return status(tenantId, deliveryId)
                .orElseThrow(() -> new IllegalStateException("notification delivery suppression status is unavailable"));
    }

    @Override
    public NotificationDeliveryOutcomeCounts suppressStaleForTask(TenantId tenantId, WorkTask task,
                                                                   LocalDateTime now) {
        if (tenantId == null || task == null || task.getId() == null || task.getTenantId() == null
                || !tenantId.sameValueAs(task.getTenantId()) || now == null) {
            throw new IllegalArgumentException("notification delivery task suppression is invalid");
        }
        int suppressed = mapper.suppressStaleForTaskWithoutDispatch(tenantId.getValue(), task.getId().getValue(),
                task.getNotificationProjectionVersion(), task.getStatus().isOpen(), now);
        int ambiguous = mapper.markStaleForTaskAmbiguous(tenantId.getValue(), task.getId().getValue(),
                task.getNotificationProjectionVersion(), task.getStatus().isOpen(), now);
        return new NotificationDeliveryOutcomeCounts(suppressed, ambiguous, 0);
    }

    @Override
    public NotificationDeliveryOutcomeCounts suppressForOaAnnouncement(TenantId tenantId,
                                                                         OaAnnouncementId announcementId,
                                                                         LocalDateTime now) {
        if (tenantId == null || announcementId == null || now == null) {
            throw new IllegalArgumentException("OA announcement delivery suppression is invalid");
        }
        int suppressed = mapper.suppressOaAnnouncementWithoutDispatch(tenantId.getValue(),
                announcementId.getValue(), now);
        int ambiguous = mapper.markOaAnnouncementAmbiguous(tenantId.getValue(), announcementId.getValue(), now);
        return new NotificationDeliveryOutcomeCounts(suppressed, ambiguous, 0);
    }

    @Override
    public LocalDateTime currentUtc() {
        LocalDateTime value = mapper.currentUtc();
        if (value == null) throw new IllegalStateException("notification delivery database time is unavailable");
        return value;
    }

    @Override
    public NotificationDeliveryOutcomeCounts expireOverdue(LocalDateTime now, int limit) {
        if (now == null || limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("notification delivery deadline maintenance is invalid");
        }
        int dead = mapper.expireOverdueNoEffect(now, limit);
        int ambiguous = mapper.expireOverdueAmbiguousEffect(now, limit);
        ambiguous += mapper.expireOverdueDispatch(now, limit);
        return new NotificationDeliveryOutcomeCounts(0, ambiguous, dead);
    }

    @Override
    public void purgeTerminal(LocalDateTime beforeExclusive, int limit) {
        if (beforeExclusive == null || limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("notification delivery retention request is invalid");
        }
        mapper.purgeTerminal(beforeExclusive, limit);
    }

    private NotificationDeliveryClaim claimed(long tenantId, long id, int dead, int ambiguous) {
        return new NotificationDeliveryClaim(Optional.ofNullable(mapper.selectByTenantId(tenantId, id)).map(this::value),
                dead, ambiguous);
    }

    private Optional<NotificationDeliveryStatus> status(TenantId tenantId, long id) {
        return Optional.ofNullable(mapper.selectByTenantId(tenantId.getValue(), id))
                .map(row -> NotificationDeliveryStatus.valueOf(row.getStatus()));
    }

    private NotificationDelivery value(NotificationDeliveryPO row) {
        NotificationDeliverySourceKind sourceKind = NotificationDeliverySourceKind.valueOf(row.getSourceKind());
        NotificationId notificationId = row.getNotificationId() == null ? null : new NotificationId(row.getNotificationId());
        WorkTaskId workTaskId = row.getWorkTaskId() == null ? null : new WorkTaskId(row.getWorkTaskId());
        OaAnnouncementId announcementId = row.getOaAnnouncementId() == null ? null
                : new OaAnnouncementId(row.getOaAnnouncementId());
        return new NotificationDelivery(row.getId(), new TenantId(row.getTenantId()), sourceKind, notificationId,
                new MemberId(row.getRecipientActorId()), workTaskId, announcementId,
                NotificationChannel.valueOf(row.getChannel()), row.getTemplateRevision(), row.getPayloadDigest(),
                row.getEndpointDigest(), row.getEndpointDigestVersion(),
                NotificationDeliveryStatus.valueOf(row.getStatus()), row.getAttemptCount(), row.getAvailableAt(),
                row.getDeadlineAt(), row.getLeaseOwner() == null ? null : UUID.fromString(row.getLeaseOwner()),
                row.getLeaseFence(), row.getLeaseUntil(), row.getGuarantee() == null ? null
                : NotificationDispatchGuaranteeKind.valueOf(row.getGuarantee()),
                NotificationDeliveryEffectCertainty.valueOf(row.getEffectCertainty()), row.getIdempotencyUntil(),
                row.getDispatchStartedTime(), row.getTerminalTime(), row.getSafeReason());
    }

    private static void requireTransition(TenantId tenantId, long deliveryId, UUID leaseOwner, long leaseFence,
                                          LocalDateTime now, int maxAttempts, Duration retryBackoff) {
        if (tenantId == null || deliveryId < 1 || leaseOwner == null || leaseFence < 1 || now == null
                || !validAttempts(maxAttempts) || invalidDuration(retryBackoff)) {
            throw new IllegalArgumentException("notification delivery transition is invalid");
        }
    }

    private static boolean validAttempts(int value) { return value >= 1 && value <= 20; }
    private static boolean ambiguousEffect(NotificationDeliveryPO row) {
        return NotificationDeliveryEffectCertainty.AMBIGUOUS.name().equals(row.getEffectCertainty());
    }
    private static boolean invalidDuration(Duration value) { return value == null || value.isNegative() || value.isZero(); }
    private static void requireOne(int changed, String action) {
        if (changed != 1) throw new IllegalStateException(action + " was lost");
    }
}
