package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.ReturnVisitAccessResolver;
import io.github.archetom.crm.domain.entity.ReturnVisit;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.ReturnVisitAccessMode;
import io.github.archetom.crm.domain.model.ReturnVisitStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.ReturnVisitRepository;
import io.github.archetom.crm.domain.valueobject.ReturnVisitId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Return-visit attachment access reuses the current owning-domain scope for every operation. */
@Component
@RequiredArgsConstructor
public class ReturnVisitAttachmentTargetAccess implements AttachmentTargetAccess {
    private final ReturnVisitAccessResolver accessResolver;
    private final OrganizationTenantRepository tenantRepository;
    private final ReturnVisitRepository returnVisitRepository;

    @Override public AttachmentTargetType targetType() { return AttachmentTargetType.RETURN_VISIT; }

    @Override
    public TenantId authorizeRead(AuthenticatedCaller caller, AttachmentTarget target) {
        return visible(caller, target, AuthorityCatalog.RETURN_VISITS_READ, ReturnVisitAccessMode.READ);
    }

    @Override
    public TenantId authorizeCollaborate(AuthenticatedCaller caller, AttachmentTarget target) {
        return visible(caller, target, AuthorityCatalog.RETURN_VISITS_WRITE, ReturnVisitAccessMode.WRITE);
    }

    @Override
    public AttachmentTargetLifecycle lockForAttachmentMutation(
            AuthenticatedCaller caller, AttachmentTarget target, AttachmentTargetOperation operation) {
        return switch (operation) {
            case CREATE_INTENT, UPLOAD, STATUS, WITHDRAW -> lockVisible(caller, target,
                    AuthorityCatalog.RETURN_VISITS_WRITE, ReturnVisitAccessMode.WRITE);
            case DOWNLOAD -> lockVisible(caller, target,
                    AuthorityCatalog.RETURN_VISITS_READ, ReturnVisitAccessMode.READ);
            case PUBLISH, PARENT_LIFECYCLE -> throw new IllegalArgumentException(
                    "worker attachment target lock requires its dedicated internal coordinator");
        };
    }

    @Override
    public AttachmentTargetLifecycle lockForWorker(TenantId tenantId, AttachmentTarget target) {
        requireTarget(target);
        if (tenantId == null || !tenantRepository.lockForTenantChanges(tenantId)) {
            throw new IllegalStateException("attachment target tenant is unavailable");
        }
        ReturnVisit value = returnVisitRepository.findByIdForAttachmentMutation(tenantId,
                new ReturnVisitId(target.id())).orElseThrow(ReturnVisitAttachmentTargetAccess::notFound);
        return new AttachmentTargetLifecycle(tenantId, value.getStatus() == ReturnVisitStatus.ACTIVE);
    }

    private AttachmentTargetLifecycle lockVisible(AuthenticatedCaller caller, AttachmentTarget target,
                                                   String authority, ReturnVisitAccessMode mode) {
        requireTarget(target);
        var access = accessResolver.access(caller, authority, mode);
        if (!tenantRepository.lockForTenantChanges(access.tenantId())) {
            throw new IllegalStateException("attachment target tenant is unavailable");
        }
        ReturnVisit value = returnVisitRepository.findVisibleByIdForUpdate(access.tenantId(),
                new ReturnVisitId(target.id()), access.visibility()).orElseThrow(
                ReturnVisitAttachmentTargetAccess::notFound);
        return new AttachmentTargetLifecycle(access.tenantId(),
                value.getStatus() == ReturnVisitStatus.ACTIVE);
    }

    private TenantId visible(AuthenticatedCaller caller, AttachmentTarget target, String authority,
                             ReturnVisitAccessMode mode) {
        requireTarget(target);
        var access = accessResolver.access(caller, authority, mode);
        if (returnVisitRepository.findVisibleById(access.tenantId(), new ReturnVisitId(target.id()),
                access.visibility()).isEmpty()) {
            throw notFound();
        }
        return access.tenantId();
    }

    private static void requireTarget(AttachmentTarget target) {
        if (target == null || target.type() != AttachmentTargetType.RETURN_VISIT) {
            throw new IllegalArgumentException("attachment target is unsupported");
        }
    }

    private static NonRetryableApplicationException notFound() {
        return new NonRetryableApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                "Requested resource was not found");
    }
}
