package io.github.archetom.crm.application.automation;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.application.oa.OaAnnouncementPublicationClaim;
import io.github.archetom.crm.application.oa.OaAnnouncementPublicationOccurrence;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.oa.OaAnnouncementPublicationStore;
import io.github.archetom.crm.application.properties.OaAnnouncementPublicationProperties;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.application.service.impl.OaAnnouncementPublicationCoordinator;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.OperationLogPolicy;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.entity.OaAnnouncement;
import io.github.archetom.crm.domain.model.OaAnnouncementPublicationSafeReason;
import io.github.archetom.crm.domain.model.OaAnnouncementStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.OaAnnouncementRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/** Bounded durable pump for an OA-owned exact scheduled-publication occurrence. */
@Component
@RequiredArgsConstructor
public class OaAnnouncementPublicationPump implements BackgroundWorkPump {
    private final OaAnnouncementPublicationStore occurrenceStore;
    private final OaAnnouncementRepository announcementRepository;
    private final OrganizationTenantRepository tenantRepository;
    private final TenantAuthorizationEvaluator authorizationEvaluator;
    private final OaAnnouncementPublicationCoordinator coordinator;
    private final OaAnnouncementPublicationProperties properties;
    private final CommandServiceTemplate commandTemplate;
    private final BackgroundWorkObservability observability;
    private final UUID leaseOwner = UUID.randomUUID();

    @Override
    public void runOnce() {
        if (!properties.isEnabled() || !properties.valid()) return;
        purgeTerminal();
        Result<OaAnnouncementPublicationClaim> claimed = commandTemplate.execute(
                UseCaseOperation.OA_ANNOUNCEMENT_PUBLICATION_EFFECT, new ServiceOperation<>() {
                    @Override public OaAnnouncementPublicationClaim execute() {
                        LocalDateTime now = occurrenceStore.currentUtc();
                        return occurrenceStore.claimDue(leaseOwner, now,
                                properties.getLeaseDuration(), properties.getMaxAttempts());
                    }
                }, OperationLogPolicy.SAFE_BACKGROUND);
        requireSuccess(claimed, "claim");
        OaAnnouncementPublicationClaim claim = claimed.getData();
        if (claim == null) throw new IllegalStateException("OA announcement publication claim was empty");
        if (claim.occurrence().isEmpty()) return;
        observability.recordOaAnnouncementPublicationOutcome(
                OaAnnouncementPublicationMetric.CLAIMED, 1);
        OaAnnouncementPublicationOccurrence occurrence = claim.occurrence().get();
        Result<OaAnnouncementPublicationMetric> effect = commandTemplate.execute(
                UseCaseOperation.OA_ANNOUNCEMENT_PUBLICATION_EFFECT, new ServiceOperation<>() {
                    @Override public OaAnnouncementPublicationMetric execute() {
                        return publishOrCancel(claim, occurrence, occurrenceStore.currentUtc());
                    }
                }, OperationLogPolicy.SAFE_BACKGROUND);
        if (effect != null && effect.isSuccess()) {
            if (effect.getData() != null) {
                observability.recordOaAnnouncementPublicationOutcome(effect.getData(), 1);
            }
            return;
        }
        Result<Void> recovered = commandTemplate.execute(
                UseCaseOperation.OA_ANNOUNCEMENT_PUBLICATION_EFFECT, new ServiceOperation<>() {
                    @Override public Void execute() {
                        occurrenceStore.retry(occurrence.tenantId(), occurrence.id(), leaseOwner,
                                occurrence.leaseFence(), occurrenceStore.currentUtc(),
                                properties.getRetryBackoff());
                        return null;
                    }
                }, OperationLogPolicy.SAFE_BACKGROUND);
        requireSuccess(recovered, "recovery");
        observability.recordOaAnnouncementPublicationOutcome(OaAnnouncementPublicationMetric.RETRY, 1);
    }

    private OaAnnouncementPublicationMetric publishOrCancel(
            OaAnnouncementPublicationClaim claim,
            OaAnnouncementPublicationOccurrence claimed, LocalDateTime now) {
        // All manual schedule/cancel/publish/delete paths own this tenant lock first.
        if (!tenantRepository.lockForTenantChanges(claimed.tenantId())) {
            throw new IllegalStateException("OA announcement publication tenant disappeared");
        }
        Optional<OaAnnouncement> root = announcementRepository.findByIdForUpdate(
                claimed.tenantId(), claimed.announcementId());
        Optional<OaAnnouncementPublicationOccurrence> locked = occurrenceStore.findClaimedForUpdate(
                claimed.tenantId(), claimed.id(), leaseOwner, claimed.leaseFence(), now);
        if (locked.isEmpty()) return null;
        OaAnnouncementPublicationOccurrence occurrence = locked.get();
        if (root.isEmpty() || root.get().getStatus() != OaAnnouncementStatus.SCHEDULED
                || root.get().getPublicationGeneration() != occurrence.publicationGeneration()
                || !root.get().getScheduledPublishTime().equals(occurrence.dueTime())) {
            occurrenceStore.markCancelled(occurrence.tenantId(), occurrence.id(), leaseOwner,
                    occurrence.leaseFence(), now, OaAnnouncementPublicationSafeReason.SUPERSEDED);
            return OaAnnouncementPublicationMetric.CANCELLED;
        }
        OaAnnouncement announcement = root.get();
        if (claim.terminalClaim()) {
            OaAnnouncementPublicationSafeReason reason = !occurrence.deadlineAt().isAfter(now)
                    ? OaAnnouncementPublicationSafeReason.MISFIRE_EXPIRED
                    : OaAnnouncementPublicationSafeReason.RETRY_EXHAUSTED;
            announcement.failScheduledPublication(occurrence.publicationGeneration(), reason, now);
            announcementRepository.save(occurrence.tenantId(), announcement);
            occurrenceStore.markDead(occurrence.tenantId(), occurrence.id(), leaseOwner,
                    occurrence.leaseFence(), now, reason);
            return reason == OaAnnouncementPublicationSafeReason.MISFIRE_EXPIRED
                    ? OaAnnouncementPublicationMetric.MISFIRE
                    : OaAnnouncementPublicationMetric.DEAD;
        }
        if (!canPublish(occurrence.tenantId(), announcement)) {
            announcement.failScheduledPublication(occurrence.publicationGeneration(),
                    OaAnnouncementPublicationSafeReason.SCHEDULING_MEMBER_UNAUTHORIZED, now);
            announcementRepository.save(occurrence.tenantId(), announcement);
            occurrenceStore.markCancelled(occurrence.tenantId(), occurrence.id(), leaseOwner,
                    occurrence.leaseFence(), now,
                    OaAnnouncementPublicationSafeReason.SCHEDULING_MEMBER_UNAUTHORIZED);
            return OaAnnouncementPublicationMetric.CANCELLED;
        }
        OaAnnouncementPublicationCoordinator.PublicationResult result =
                coordinator.publish(announcement, now);
        if (!result.published()) {
            announcement.failScheduledPublication(occurrence.publicationGeneration(),
                    result.rejectionReason(), now);
            announcementRepository.save(occurrence.tenantId(), announcement);
            occurrenceStore.markCancelled(occurrence.tenantId(), occurrence.id(), leaseOwner,
                    occurrence.leaseFence(), now, result.rejectionReason());
            return OaAnnouncementPublicationMetric.CANCELLED;
        }
        occurrenceStore.markPublished(occurrence.tenantId(), occurrence.id(), leaseOwner,
                occurrence.leaseFence(), now);
        return OaAnnouncementPublicationMetric.PUBLISHED;
    }

    private boolean canPublish(io.github.archetom.crm.domain.valueobject.TenantId tenantId,
                               OaAnnouncement announcement) {
        if (announcement.getScheduledByMemberId() == null) return false;
        TenantAuthorizationEvaluator.Evaluation evaluation = authorizationEvaluator.evaluate(
                tenantId, announcement.getScheduledByMemberId());
        return evaluation.activeMember() && evaluation.effectiveGrants().stream().anyMatch(grant ->
                AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE.equals(grant.authorityCode().getValue()));
    }

    private void purgeTerminal() {
        Result<Void> result = commandTemplate.execute(
                UseCaseOperation.OA_ANNOUNCEMENT_PUBLICATION_EFFECT, new ServiceOperation<>() {
                    @Override public Void execute() {
                        LocalDateTime now = occurrenceStore.currentUtc();
                        occurrenceStore.purgeTerminal(now.minus(properties.getTerminalRetention()), 100);
                        return null;
                    }
                }, OperationLogPolicy.SAFE_BACKGROUND);
        requireSuccess(result, "retention");
    }

    private static <T> void requireSuccess(Result<T> result, String stage) {
        if (result == null || !result.isSuccess()) {
            throw new IllegalStateException("OA announcement publication " + stage + " failed");
        }
    }
}
