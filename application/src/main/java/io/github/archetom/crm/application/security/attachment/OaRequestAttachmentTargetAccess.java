package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.OaRequestAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.entity.OaRequest;
import io.github.archetom.crm.domain.model.ApprovalInstanceStatus;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.ApprovalInstanceRepository;
import io.github.archetom.crm.domain.repository.OaRequestRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaRequestId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * B29d adapter. Read visibility is the existing OA participation predicate; pending write
 * collaboration is intentionally restricted to the applicant, current task assignees and the
 * instance-manager snapshot. Every lock path follows approval instance -> OA request -> file.
 */
@Component
@RequiredArgsConstructor
public class OaRequestAttachmentTargetAccess implements AttachmentTargetAccess {
    private final OrganizationAccessGuard accessGuard;
    private final OaRequestAccessResolver requestAccessResolver;
    private final OrganizationTenantRepository tenantRepository;
    private final OaRequestRepository requestRepository;
    private final ApprovalInstanceRepository instanceRepository;

    @Override
    public AttachmentTargetType targetType() {
        return AttachmentTargetType.OA_REQUEST;
    }

    @Override
    public TenantId authorizeRead(AuthenticatedCaller caller, AttachmentTarget target) {
        return visible(caller, target).tenantId();
    }

    @Override
    public TenantId authorizeCollaborate(AuthenticatedCaller caller, AttachmentTarget target) {
        OaRequestAccessResolver.OaRequestAccess access = collaborationAccess(caller, target);
        OaRequest request = requireVisible(access, target);
        ApprovalInstance instance = instanceRepository.findById(access.tenantId(),
                        request.getApprovalInstanceId())
                .orElseThrow(OaRequestAttachmentTargetAccess::notFound);
        requireCurrentCollaborator(instance, access.memberId());
        return access.tenantId();
    }

    @Override
    public AttachmentTargetLifecycle lockForAttachmentMutation(
            AuthenticatedCaller caller, AttachmentTarget target, AttachmentTargetOperation operation) {
        requireTarget(target);
        return switch (operation) {
            case CREATE_INTENT, UPLOAD, STATUS, WITHDRAW -> lockCollaborating(caller, target);
            case DOWNLOAD -> lockReadable(caller, target);
            case PUBLISH, PARENT_LIFECYCLE -> throw new IllegalArgumentException(
                    "worker attachment target lock requires its dedicated internal coordinator");
        };
    }

    @Override
    public AttachmentTargetLifecycle lockForWorker(TenantId tenantId, AttachmentTarget target) {
        requireTarget(target);
        lockTenant(tenantId);
        OaRequest routed = requestRepository.findById(tenantId, id(target))
                .orElseThrow(OaRequestAttachmentTargetAccess::notFound);
        LockedRequest locked = lockExact(tenantId, target, routed.getApprovalInstanceId());
        return new AttachmentTargetLifecycle(tenantId,
                locked.instance().getStatus() == ApprovalInstanceStatus.PENDING);
    }

    private AttachmentTargetLifecycle lockCollaborating(
            AuthenticatedCaller caller, AttachmentTarget target) {
        TenantId tenantId = collaborationTenant(caller, target);
        lockTenant(tenantId);
        // No membership or request read occurs before the tenant lock. The following membership
        // reads use FOR SHARE and therefore observe the current committed membership state.
        OaRequestAccessResolver.OaRequestAccess access = collaborationAccessForShare(caller, target);
        OaRequest visible = requireVisible(access, target);
        LockedRequest locked = lockExact(access.tenantId(), target, visible.getApprovalInstanceId());
        requireCurrentCollaborator(locked.instance(), access.memberId());
        return new AttachmentTargetLifecycle(access.tenantId(), true);
    }

    private AttachmentTargetLifecycle lockReadable(AuthenticatedCaller caller, AttachmentTarget target) {
        TenantId tenantId = requestAccessResolver.tenant(caller);
        lockTenant(tenantId);
        // A tenant-level membership change must not race a download authorization.
        OaRequestAccessResolver.OaRequestAccess access = visibleForShare(caller, target);
        OaRequest visible = requireVisible(access, target);
        lockExact(access.tenantId(), target, visible.getApprovalInstanceId());
        // Terminal files remain downloadable; generic queries/downloads further restrict to AVAILABLE.
        return new AttachmentTargetLifecycle(access.tenantId(), true);
    }

    private OaRequestAccessResolver.OaRequestAccess collaborationAccess(
            AuthenticatedCaller caller, AttachmentTarget target) {
        requireTarget(target);
        if (caller == null || !caller.hasAuthority(AuthorityCatalog.OA_REQUESTS_COLLABORATE)) {
            throw notFound();
        }
        TenantId collaboratorTenant = accessGuard.requireActiveMembership(caller,
                AuthorityCatalog.OA_REQUESTS_COLLABORATE);
        OaRequestAccessResolver.OaRequestAccess access = visible(caller, target);
        if (!collaboratorTenant.sameValueAs(access.tenantId())) {
            throw notFound();
        }
        return access;
    }

    private TenantId collaborationTenant(AuthenticatedCaller caller, AttachmentTarget target) {
        requireTarget(target);
        if (caller == null || !caller.hasAuthority(AuthorityCatalog.OA_REQUESTS_COLLABORATE)) {
            throw notFound();
        }
        return accessGuard.requireTenant(caller, AuthorityCatalog.OA_REQUESTS_COLLABORATE);
    }

    private OaRequestAccessResolver.OaRequestAccess collaborationAccessForShare(
            AuthenticatedCaller caller, AttachmentTarget target) {
        TenantId collaboratorTenant = accessGuard.requireActiveMembershipForShare(caller,
                AuthorityCatalog.OA_REQUESTS_COLLABORATE);
        OaRequestAccessResolver.OaRequestAccess access = visibleForShare(caller, target);
        if (!collaboratorTenant.sameValueAs(access.tenantId())) {
            throw notFound();
        }
        return access;
    }

    private OaRequestAccessResolver.OaRequestAccess visible(
            AuthenticatedCaller caller, AttachmentTarget target) {
        requireTarget(target);
        OaRequestAccessResolver.OaRequestAccess access = requestAccessResolver.access(caller);
        requireVisible(access, target);
        return access;
    }

    private OaRequestAccessResolver.OaRequestAccess visibleForShare(
            AuthenticatedCaller caller, AttachmentTarget target) {
        requireTarget(target);
        OaRequestAccessResolver.OaRequestAccess access = requestAccessResolver.accessForShare(caller);
        requireVisible(access, target);
        return access;
    }

    private OaRequest requireVisible(OaRequestAccessResolver.OaRequestAccess access,
                                     AttachmentTarget target) {
        return requestRepository.findVisibleById(access.tenantId(), id(target), access.memberId(),
                access.tenantManager()).orElseThrow(OaRequestAttachmentTargetAccess::notFound);
    }

    private LockedRequest lockExact(TenantId tenantId, AttachmentTarget target,
                                    ApprovalInstanceId routedApprovalId) {
        if (routedApprovalId == null) {
            throw notFound();
        }
        ApprovalInstance instance = instanceRepository.findByIdForUpdate(tenantId, routedApprovalId)
                .orElseThrow(OaRequestAttachmentTargetAccess::notFound);
        OaRequest request = requestRepository.findByIdForUpdate(tenantId, id(target))
                .filter(value -> routedApprovalId.equals(value.getApprovalInstanceId()))
                .orElseThrow(OaRequestAttachmentTargetAccess::notFound);
        return new LockedRequest(request, instance);
    }

    private static void requireCurrentCollaborator(ApprovalInstance instance, MemberId memberId) {
        if (instance.getStatus() != ApprovalInstanceStatus.PENDING
                || !instance.isCurrentOaAttachmentCollaborator(memberId)) {
            throw notFound();
        }
    }

    private static OaRequestId id(AttachmentTarget target) {
        return new OaRequestId(target.id());
    }

    private void lockTenant(TenantId tenantId) {
        if (tenantId == null || !tenantRepository.lockForTenantChanges(tenantId)) {
            throw new IllegalStateException("attachment target tenant is unavailable");
        }
    }

    private static void requireTarget(AttachmentTarget target) {
        if (target == null || target.type() != AttachmentTargetType.OA_REQUEST) {
            throw new IllegalArgumentException("attachment target is unsupported");
        }
    }

    private static NonRetryableApplicationException notFound() {
        return new NonRetryableApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                "Requested resource was not found");
    }

    private record LockedRequest(OaRequest request, ApprovalInstance instance) {
    }
}
