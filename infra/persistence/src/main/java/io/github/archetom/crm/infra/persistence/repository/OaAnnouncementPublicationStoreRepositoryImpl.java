package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.archetom.crm.application.oa.OaAnnouncementPublicationClaim;
import io.github.archetom.crm.application.oa.OaAnnouncementPublicationOccurrence;
import io.github.archetom.crm.application.oa.OaAnnouncementPublicationOccurrenceStatus;
import io.github.archetom.crm.application.port.out.oa.OaAnnouncementPublicationStore;
import io.github.archetom.crm.domain.model.OaAnnouncementPublicationSafeReason;
import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OaAnnouncementPublicationOccurrenceMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementPublicationOccurrencePO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/** MySQL implementation of OA's own one-shot lease/fence ledger. */
@Repository
@RequiredArgsConstructor
public class OaAnnouncementPublicationStoreRepositoryImpl
        implements OaAnnouncementPublicationStore {
    private static final int CLAIM_CLEANUP_LIMIT = 10;
    private final OaAnnouncementPublicationOccurrenceMapper mapper;

    @Override
    public void create(TenantId tenantId, OaAnnouncementId announcementId,
                       long publicationGeneration, LocalDateTime dueTime,
                       LocalDateTime deadlineAt, LocalDateTime createdTime) {
        if (tenantId == null || announcementId == null || publicationGeneration < 1
                || dueTime == null || deadlineAt == null || !deadlineAt.isAfter(dueTime)
                || createdTime == null) {
            throw new IllegalArgumentException("OA announcement publication occurrence is invalid");
        }
        OaAnnouncementPublicationOccurrencePO row = new OaAnnouncementPublicationOccurrencePO();
        row.setTenantId(tenantId.getValue());
        row.setAnnouncementId(announcementId.getValue());
        row.setPublicationGeneration(publicationGeneration);
        row.setDueTime(dueTime);
        row.setStatus(OaAnnouncementPublicationOccurrenceStatus.READY.name());
        row.setAttemptCount(0);
        row.setAvailableAt(dueTime);
        row.setDeadlineAt(deadlineAt);
        row.setLeaseFence(0L);
        row.setCreatedTime(createdTime);
        try {
            if (mapper.insert(row) != 1) {
                throw new IllegalStateException("OA announcement publication occurrence insert failed");
            }
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("OA announcement publication generation conflicts", exception);
        }
    }

    @Override
    public boolean cancelGeneration(TenantId tenantId, OaAnnouncementId announcementId,
                                    long publicationGeneration, LocalDateTime now,
                                    OaAnnouncementPublicationSafeReason reason) {
        if (tenantId == null || announcementId == null || publicationGeneration < 1
                || now == null || reason == null) {
            throw new IllegalArgumentException("OA announcement publication cancellation is invalid");
        }
        requireCancellationReason(reason);
        int changed = mapper.update(null, new LambdaUpdateWrapper<OaAnnouncementPublicationOccurrencePO>()
                .eq(OaAnnouncementPublicationOccurrencePO::getTenantId, tenantId.getValue())
                .eq(OaAnnouncementPublicationOccurrencePO::getAnnouncementId, announcementId.getValue())
                .eq(OaAnnouncementPublicationOccurrencePO::getPublicationGeneration, publicationGeneration)
                .in(OaAnnouncementPublicationOccurrencePO::getStatus,
                        OaAnnouncementPublicationOccurrenceStatus.READY.name(),
                        OaAnnouncementPublicationOccurrenceStatus.RETRY_WAIT.name(),
                        OaAnnouncementPublicationOccurrenceStatus.LEASED.name())
                .set(OaAnnouncementPublicationOccurrencePO::getStatus,
                        OaAnnouncementPublicationOccurrenceStatus.CANCELLED.name())
                .set(OaAnnouncementPublicationOccurrencePO::getLeaseOwner, null)
                .set(OaAnnouncementPublicationOccurrencePO::getLeaseUntil, null)
                .set(OaAnnouncementPublicationOccurrencePO::getSafeReason, reason.name())
                .set(OaAnnouncementPublicationOccurrencePO::getTerminalTime, now));
        if (changed < 0 || changed > 1) {
            throw new IllegalStateException("OA announcement publication cancellation affected multiple occurrences");
        }
        return changed == 1;
    }

    @Override
    public OaAnnouncementPublicationClaim claimDue(UUID leaseOwner, LocalDateTime now,
                                                    Duration leaseDuration, int maxAttempts) {
        if (leaseOwner == null || now == null || leaseDuration == null || leaseDuration.isNegative()
                || leaseDuration.isZero() || maxAttempts < 1 || maxAttempts > 20) {
            throw new IllegalArgumentException("OA announcement publication claim is invalid");
        }
        for (int skipped = 0; skipped < CLAIM_CLEANUP_LIMIT; skipped++) {
            OaAnnouncementPublicationOccurrencePO candidate =
                    mapper.selectClaimableForUpdate(now, maxAttempts);
            if (candidate == null) return OaAnnouncementPublicationClaim.none();
            if (OaAnnouncementPublicationOccurrenceStatus.LEASED.name().equals(candidate.getStatus())) {
                if (mapper.requeueExpiredLease(candidate.getId(), now) != 1) {
                    throw new IllegalStateException("OA announcement publication lease recovery was lost");
                }
                continue;
            }
            boolean terminal = !candidate.getDeadlineAt().isAfter(now)
                    || candidate.getAttemptCount() >= maxAttempts;
            LocalDateTime leaseUntil = terminal ? now.plus(leaseDuration)
                    : min(now.plus(leaseDuration), candidate.getDeadlineAt());
            int changed = terminal
                    ? mapper.claimTerminal(candidate.getId(), leaseOwner.toString(), leaseUntil)
                    : mapper.claimReady(candidate.getId(), leaseOwner.toString(), leaseUntil, now, maxAttempts);
            if (changed != 1) {
                throw new IllegalStateException("OA announcement publication claim was lost");
            }
            OaAnnouncementPublicationOccurrencePO claimed = mapper.selectById(candidate.getId());
            if (claimed == null) throw new IllegalStateException("OA announcement publication claim disappeared");
            return new OaAnnouncementPublicationClaim(Optional.of(domain(claimed)), terminal);
        }
        return OaAnnouncementPublicationClaim.none();
    }

    @Override
    public Optional<OaAnnouncementPublicationOccurrence> findClaimedForUpdate(
            TenantId tenantId, Long occurrenceId, UUID leaseOwner,
            long leaseFence, LocalDateTime now) {
        if (tenantId == null || occurrenceId == null || occurrenceId < 1 || leaseOwner == null
                || leaseFence < 1 || now == null) return Optional.empty();
        return Optional.ofNullable(mapper.selectOne(
                new LambdaQueryWrapper<OaAnnouncementPublicationOccurrencePO>()
                        .eq(OaAnnouncementPublicationOccurrencePO::getTenantId, tenantId.getValue())
                        .eq(OaAnnouncementPublicationOccurrencePO::getId, occurrenceId)
                        .eq(OaAnnouncementPublicationOccurrencePO::getStatus,
                                OaAnnouncementPublicationOccurrenceStatus.LEASED.name())
                        .eq(OaAnnouncementPublicationOccurrencePO::getLeaseOwner, leaseOwner.toString())
                        .eq(OaAnnouncementPublicationOccurrencePO::getLeaseFence, leaseFence)
                        .gt(OaAnnouncementPublicationOccurrencePO::getLeaseUntil, now)
                        .last("FOR UPDATE"))).map(this::domain);
    }

    @Override
    public void markPublished(TenantId tenantId, Long occurrenceId, UUID leaseOwner,
                              long leaseFence, LocalDateTime now) {
        transitionTerminal(tenantId, occurrenceId, leaseOwner, leaseFence,
                OaAnnouncementPublicationOccurrenceStatus.PUBLISHED, now, null, true);
    }

    @Override
    public void markCancelled(TenantId tenantId, Long occurrenceId, UUID leaseOwner,
                              long leaseFence, LocalDateTime now,
                              OaAnnouncementPublicationSafeReason reason) {
        transitionTerminal(tenantId, occurrenceId, leaseOwner, leaseFence,
                OaAnnouncementPublicationOccurrenceStatus.CANCELLED, now, reason, false);
    }

    @Override
    public void markDead(TenantId tenantId, Long occurrenceId, UUID leaseOwner,
                         long leaseFence, LocalDateTime now,
                         OaAnnouncementPublicationSafeReason reason) {
        transitionTerminal(tenantId, occurrenceId, leaseOwner, leaseFence,
                OaAnnouncementPublicationOccurrenceStatus.DEAD, now, reason, false);
    }

    @Override
    public void retry(TenantId tenantId, Long occurrenceId, UUID leaseOwner,
                      long leaseFence, LocalDateTime now, Duration retryBackoff) {
        if (tenantId == null || occurrenceId == null || occurrenceId < 1 || leaseOwner == null
                || leaseFence < 1 || now == null || retryBackoff == null
                || retryBackoff.isNegative() || retryBackoff.isZero()) {
            throw new IllegalArgumentException("OA announcement publication retry is invalid");
        }
        int changed = mapper.update(null, new LambdaUpdateWrapper<OaAnnouncementPublicationOccurrencePO>()
                .eq(OaAnnouncementPublicationOccurrencePO::getTenantId, tenantId.getValue())
                .eq(OaAnnouncementPublicationOccurrencePO::getId, occurrenceId)
                .eq(OaAnnouncementPublicationOccurrencePO::getStatus,
                        OaAnnouncementPublicationOccurrenceStatus.LEASED.name())
                .eq(OaAnnouncementPublicationOccurrencePO::getLeaseOwner, leaseOwner.toString())
                .eq(OaAnnouncementPublicationOccurrencePO::getLeaseFence, leaseFence)
                .apply("lease_until > UTC_TIMESTAMP(6)")
                .set(OaAnnouncementPublicationOccurrencePO::getStatus,
                        OaAnnouncementPublicationOccurrenceStatus.RETRY_WAIT.name())
                .set(OaAnnouncementPublicationOccurrencePO::getAvailableAt, now.plus(retryBackoff))
                .set(OaAnnouncementPublicationOccurrencePO::getLeaseOwner, null)
                .set(OaAnnouncementPublicationOccurrencePO::getLeaseUntil, null)
                .set(OaAnnouncementPublicationOccurrencePO::getSafeReason,
                        OaAnnouncementPublicationSafeReason.RETRY.name()));
        if (changed != 1) throw new IllegalStateException("OA announcement publication retry lease was lost");
    }

    @Override
    public LocalDateTime currentUtc() {
        LocalDateTime value = mapper.currentUtc();
        if (value == null) throw new IllegalStateException("OA announcement publication database time is unavailable");
        return value;
    }

    @Override
    public void purgeTerminal(LocalDateTime beforeExclusive, int limit) {
        if (beforeExclusive == null || limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("OA announcement publication retention is invalid");
        }
        mapper.purgeTerminal(beforeExclusive, limit);
    }

    private void transitionTerminal(TenantId tenantId, Long occurrenceId, UUID leaseOwner,
                                    long leaseFence, OaAnnouncementPublicationOccurrenceStatus status,
                                    LocalDateTime now, OaAnnouncementPublicationSafeReason reason,
                                    boolean requireBeforeDeadline) {
        if (tenantId == null || occurrenceId == null || occurrenceId < 1 || leaseOwner == null
                || leaseFence < 1 || now == null || status == null) {
            throw new IllegalArgumentException("OA announcement publication terminal transition is invalid");
        }
        requireTerminalReason(status, reason);
        int changed = mapper.update(null, new LambdaUpdateWrapper<OaAnnouncementPublicationOccurrencePO>()
                .eq(OaAnnouncementPublicationOccurrencePO::getTenantId, tenantId.getValue())
                .eq(OaAnnouncementPublicationOccurrencePO::getId, occurrenceId)
                .eq(OaAnnouncementPublicationOccurrencePO::getStatus,
                        OaAnnouncementPublicationOccurrenceStatus.LEASED.name())
                .eq(OaAnnouncementPublicationOccurrencePO::getLeaseOwner, leaseOwner.toString())
                .eq(OaAnnouncementPublicationOccurrencePO::getLeaseFence, leaseFence)
                .apply("lease_until > UTC_TIMESTAMP(6)")
                .apply(requireBeforeDeadline, "deadline_at > UTC_TIMESTAMP(6)")
                .set(OaAnnouncementPublicationOccurrencePO::getStatus, status.name())
                .set(OaAnnouncementPublicationOccurrencePO::getLeaseOwner, null)
                .set(OaAnnouncementPublicationOccurrencePO::getLeaseUntil, null)
                .set(OaAnnouncementPublicationOccurrencePO::getTerminalTime, now)
                .set(OaAnnouncementPublicationOccurrencePO::getSafeReason,
                        reason == null ? null : reason.name()));
        if (changed != 1) throw new IllegalStateException("OA announcement publication lease was lost");
    }

    private static void requireTerminalReason(OaAnnouncementPublicationOccurrenceStatus status,
                                              OaAnnouncementPublicationSafeReason reason) {
        if (status == OaAnnouncementPublicationOccurrenceStatus.PUBLISHED) {
            if (reason == null) return;
        } else if (status == OaAnnouncementPublicationOccurrenceStatus.CANCELLED
                && isCancellationReason(reason)) {
            return;
        } else if (status == OaAnnouncementPublicationOccurrenceStatus.DEAD
                && (reason == OaAnnouncementPublicationSafeReason.MISFIRE_EXPIRED
                || reason == OaAnnouncementPublicationSafeReason.RETRY_EXHAUSTED)) {
            return;
        }
        throw new IllegalArgumentException("OA announcement publication terminal reason is invalid");
    }

    private static void requireCancellationReason(OaAnnouncementPublicationSafeReason reason) {
        if (!isCancellationReason(reason)) {
            throw new IllegalArgumentException("OA announcement publication cancellation reason is invalid");
        }
    }

    private static boolean isCancellationReason(OaAnnouncementPublicationSafeReason reason) {
        return reason == OaAnnouncementPublicationSafeReason.SCHEDULING_MEMBER_UNAUTHORIZED
                || reason == OaAnnouncementPublicationSafeReason.AUDIENCE_INVALID
                || reason == OaAnnouncementPublicationSafeReason.AUDIENCE_EMPTY
                || reason == OaAnnouncementPublicationSafeReason.AUDIENCE_LIMIT
                || reason == OaAnnouncementPublicationSafeReason.ATTACHMENT_UNAVAILABLE
                || reason == OaAnnouncementPublicationSafeReason.CANCELLED
                || reason == OaAnnouncementPublicationSafeReason.SUPERSEDED;
    }

    private OaAnnouncementPublicationOccurrence domain(OaAnnouncementPublicationOccurrencePO row) {
        return new OaAnnouncementPublicationOccurrence(row.getId(), new TenantId(row.getTenantId()),
                new OaAnnouncementId(row.getAnnouncementId()), row.getPublicationGeneration(), row.getDueTime(),
                OaAnnouncementPublicationOccurrenceStatus.valueOf(row.getStatus()), row.getAttemptCount(),
                row.getAvailableAt(), row.getDeadlineAt(),
                row.getLeaseOwner() == null ? null : UUID.fromString(row.getLeaseOwner()), row.getLeaseFence(),
                row.getLeaseUntil(), row.getCreatedTime(), row.getTerminalTime(),
                row.getSafeReason() == null ? null : OaAnnouncementPublicationSafeReason.valueOf(row.getSafeReason()));
    }

    private static LocalDateTime min(LocalDateTime first, LocalDateTime second) {
        return first.isAfter(second) ? second : first;
    }
}
