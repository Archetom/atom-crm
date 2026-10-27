package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.domain.entity.OaAnnouncement;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.OaAnnouncementStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.OaAnnouncementRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * OA attachments belong to the announcement root, never to a recipient copy.  Draft managers
 * and published recipients deliberately use separate authority and lifecycle predicates.
 */
@Component
@RequiredArgsConstructor
public class OaAnnouncementAttachmentTargetAccess implements AttachmentTargetAccess {
    private final OrganizationAccessGuard accessGuard;
    private final OrganizationTenantRepository tenantRepository;
    private final OaAnnouncementRepository announcementRepository;

    @Override
    public AttachmentTargetType targetType() {
        return AttachmentTargetType.OA_ANNOUNCEMENT;
    }

    @Override
    public TenantId authorizeRead(AuthenticatedCaller caller, AttachmentTarget target) {
        requireTarget(target);
        TenantId managerTenant = draftManagerTenant(caller, target, false);
        if (managerTenant != null) return managerTenant;
        TenantId recipientTenant = publishedRecipientTenant(caller, target, false);
        if (recipientTenant != null) return recipientTenant;
        throw notFound();
    }

    @Override
    public TenantId authorizeCollaborate(AuthenticatedCaller caller, AttachmentTarget target) {
        requireTarget(target);
        TenantId tenantId = draftManagerTenant(caller, target, false);
        if (tenantId == null) throw notFound();
        return tenantId;
    }

    @Override
    public AttachmentTargetLifecycle lockForAttachmentMutation(
            AuthenticatedCaller caller, AttachmentTarget target, AttachmentTargetOperation operation) {
        requireTarget(target);
        return switch (operation) {
            case CREATE_INTENT, UPLOAD, STATUS, WITHDRAW -> lockDraftManager(caller, target);
            case DOWNLOAD -> lockReadable(caller, target);
            case PUBLISH, PARENT_LIFECYCLE -> throw new IllegalArgumentException(
                    "worker attachment target lock requires its dedicated internal coordinator");
        };
    }

    @Override
    public AttachmentTargetLifecycle lockForWorker(TenantId tenantId, AttachmentTarget target) {
        requireTarget(target);
        lockTenant(tenantId);
        OaAnnouncement announcement = announcementRepository.findByIdForUpdate(tenantId, id(target))
                .orElseThrow(OaAnnouncementAttachmentTargetAccess::notFound);
        return new AttachmentTargetLifecycle(tenantId,
                announcement.getStatus() == OaAnnouncementStatus.DRAFT);
    }

    private AttachmentTargetLifecycle lockDraftManager(
            AuthenticatedCaller caller, AttachmentTarget target) {
        if (caller == null || !caller.hasAuthority(AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE)) {
            throw notFound();
        }
        TenantId tenantId = accessGuard.requireActiveMembership(caller,
                AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE);
        lockTenant(tenantId);
        OaAnnouncement announcement = announcementRepository.findByIdForUpdate(tenantId, id(target))
                .orElseThrow(OaAnnouncementAttachmentTargetAccess::notFound);
        if (announcement.getStatus() != OaAnnouncementStatus.DRAFT) throw notFound();
        return new AttachmentTargetLifecycle(tenantId, true);
    }

    private AttachmentTargetLifecycle lockReadable(AuthenticatedCaller caller, AttachmentTarget target) {
        if (caller != null && caller.hasAuthority(AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE)) {
            TenantId tenantId = accessGuard.requireActiveMembership(caller,
                    AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE);
            lockTenant(tenantId);
            OaAnnouncement announcement = announcementRepository.findByIdForUpdate(tenantId, id(target))
                    .orElseThrow(OaAnnouncementAttachmentTargetAccess::notFound);
            if (announcement.getStatus() == OaAnnouncementStatus.DRAFT) {
                return new AttachmentTargetLifecycle(tenantId, true);
            }
        }
        if (caller != null && caller.hasAuthority(AuthorityCatalog.OA_ANNOUNCEMENTS_READ)) {
            TenantId tenantId = accessGuard.requireActiveMembership(caller,
                    AuthorityCatalog.OA_ANNOUNCEMENTS_READ);
            lockTenant(tenantId);
            OaAnnouncement announcement = announcementRepository.findByIdForUpdate(tenantId, id(target))
                    .orElseThrow(OaAnnouncementAttachmentTargetAccess::notFound);
            if (announcement.getStatus() == OaAnnouncementStatus.PUBLISHED
                    && announcementRepository.hasRecipientSnapshot(tenantId, id(target),
                    new MemberId(caller.actorId()))) {
                return new AttachmentTargetLifecycle(tenantId, true);
            }
        }
        throw notFound();
    }

    private TenantId draftManagerTenant(
            AuthenticatedCaller caller, AttachmentTarget target, boolean forUpdate) {
        if (caller == null || !caller.hasAuthority(AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE)) return null;
        TenantId tenantId = accessGuard.requireActiveMembership(caller,
                AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE);
        OaAnnouncement announcement = (forUpdate
                ? announcementRepository.findByIdForUpdate(tenantId, id(target))
                : announcementRepository.findById(tenantId, id(target)))
                .orElse(null);
        return announcement != null && announcement.getStatus() == OaAnnouncementStatus.DRAFT
                ? tenantId : null;
    }

    private TenantId publishedRecipientTenant(
            AuthenticatedCaller caller, AttachmentTarget target, boolean forUpdate) {
        if (caller == null || !caller.hasAuthority(AuthorityCatalog.OA_ANNOUNCEMENTS_READ)) return null;
        TenantId tenantId = accessGuard.requireActiveMembership(caller,
                AuthorityCatalog.OA_ANNOUNCEMENTS_READ);
        OaAnnouncement announcement = (forUpdate
                ? announcementRepository.findByIdForUpdate(tenantId, id(target))
                : announcementRepository.findById(tenantId, id(target)))
                .orElse(null);
        if (announcement == null || announcement.getStatus() != OaAnnouncementStatus.PUBLISHED) return null;
        return announcementRepository.hasRecipientSnapshot(tenantId, id(target),
                new MemberId(caller.actorId())) ? tenantId : null;
    }

    private void lockTenant(TenantId tenantId) {
        if (tenantId == null || !tenantRepository.lockForTenantChanges(tenantId)) {
            throw new IllegalStateException("attachment target tenant is unavailable");
        }
    }

    private static OaAnnouncementId id(AttachmentTarget target) {
        return new OaAnnouncementId(target.id());
    }

    private static void requireTarget(AttachmentTarget target) {
        if (target == null || target.type() != AttachmentTargetType.OA_ANNOUNCEMENT) {
            throw new IllegalArgumentException("attachment target is unsupported");
        }
    }

    private static NonRetryableApplicationException notFound() {
        return new NonRetryableApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                "Requested resource was not found");
    }
}
