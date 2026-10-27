package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.domain.entity.CrmImportDraft;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.CrmImportObjectType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.CrmImportDraftRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.valueobject.CrmImportDraftId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * A source CSV is visible only to its creating member while the one-shot import draft remains
 * active. Business-object write authority is checked again here; generic attachment access is
 * never sufficient to import CRM data.
 */
@Component
@RequiredArgsConstructor
public class CrmImportDraftAttachmentTargetAccess implements AttachmentTargetAccess {
    private final CrmImportDraftRepository draftRepository;
    private final OrganizationTenantRepository tenantRepository;
    private final OrganizationAccessGuard accessGuard;
    private final Clock clock;

    @Override public AttachmentTargetType targetType() { return AttachmentTargetType.IMPORT_DRAFT; }

    @Override
    public TenantId authorizeRead(AuthenticatedCaller caller, AttachmentTarget target) {
        return authorize(caller, target, false);
    }

    @Override
    public TenantId authorizeCollaborate(AuthenticatedCaller caller, AttachmentTarget target) {
        return authorize(caller, target, true);
    }

    @Override
    public AttachmentTargetLifecycle lockForAttachmentMutation(
            AuthenticatedCaller caller, AttachmentTarget target, AttachmentTargetOperation operation) {
        if (operation == AttachmentTargetOperation.PUBLISH || operation == AttachmentTargetOperation.PARENT_LIFECYCLE) {
            throw new IllegalArgumentException("CRM import attachment worker lock is required");
        }
        TenantId tenantId = authorize(caller, target, true);
        return lock(tenantId, target);
    }

    @Override
    public AttachmentTargetLifecycle lockForWorker(TenantId tenantId, AttachmentTarget target) {
        requireTarget(target);
        return lock(tenantId, target);
    }

    private TenantId authorize(AuthenticatedCaller caller, AttachmentTarget target, boolean forUpdate) {
        requireTarget(target);
        CrmImportDraft draft = find(caller, target, forUpdate);
        TenantId tenantId = accessGuard.requireActiveMembership(caller, authority(draft.getObjectType()));
        if (!tenantId.sameValueAs(draft.getTenantId()) || !draft.isActiveFor(new MemberId(caller.actorId()), now())) {
            throw missing();
        }
        return tenantId;
    }

    private AttachmentTargetLifecycle lock(TenantId tenantId, AttachmentTarget target) {
        requireTarget(target);
        if (tenantId == null || !tenantRepository.lockForTenantChanges(tenantId)) {
            throw new IllegalStateException("attachment target tenant is unavailable");
        }
        CrmImportDraft draft = draftRepository.findForUpdate(tenantId, new CrmImportDraftId(target.id()))
                .orElseThrow(CrmImportDraftAttachmentTargetAccess::missing);
        return new AttachmentTargetLifecycle(tenantId,
                draft.getState() == io.github.archetom.crm.domain.model.CrmImportDraftState.ACTIVE
                        && now().isBefore(draft.getDeadline()));
    }

    private CrmImportDraft find(AuthenticatedCaller caller, AttachmentTarget target, boolean forUpdate) {
        // The tenant value from the authenticated caller is never trusted as authorization. It is
        // used only to locate a tenant-scoped draft before the object-specific authority check.
        TenantId tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
        return (forUpdate ? draftRepository.findForUpdate(tenantId, new CrmImportDraftId(target.id()))
                : draftRepository.find(tenantId, new CrmImportDraftId(target.id()))).orElseThrow(
                        CrmImportDraftAttachmentTargetAccess::missing);
    }

    private static String authority(CrmImportObjectType objectType) {
        return switch (objectType) {
            case LEAD -> AuthorityCatalog.LEADS_WRITE;
            case CONTACT -> AuthorityCatalog.CONTACTS_WRITE;
            case PRODUCT -> AuthorityCatalog.PRODUCTS_WRITE;
        };
    }

    private static void requireTarget(AttachmentTarget target) {
        if (target == null || target.type() != AttachmentTargetType.IMPORT_DRAFT) {
            throw new IllegalArgumentException("CRM import attachment target is unsupported");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static NonRetryableApplicationException missing() {
        return new NonRetryableApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                "Requested resource was not found");
    }
}
