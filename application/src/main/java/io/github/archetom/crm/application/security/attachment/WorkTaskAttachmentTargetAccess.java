package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.WorkTaskRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Work attachments use task participation alone; the optional CRM relation is never consulted. */
@Component
@RequiredArgsConstructor
public class WorkTaskAttachmentTargetAccess implements AttachmentTargetAccess {

    private final OrganizationAccessGuard organizationAccessGuard;
    private final OrganizationTenantRepository tenantRepository;
    private final WorkTaskRepository workTaskRepository;

    @Override
    public AttachmentTargetType targetType() {
        return AttachmentTargetType.WORK_TASK;
    }

    @Override
    public TenantId authorizeRead(AuthenticatedCaller caller, AttachmentTarget target) {
        return visible(caller, target, AuthorityCatalog.WORK_TASKS_READ, false);
    }

    @Override
    public TenantId authorizeCollaborate(AuthenticatedCaller caller, AttachmentTarget target) {
        return visible(caller, target, AuthorityCatalog.WORK_TASKS_WRITE, false);
    }

    @Override
    public AttachmentTargetLifecycle lockForAttachmentMutation(
            AuthenticatedCaller caller, AttachmentTarget target, AttachmentTargetOperation operation) {
        TenantId tenantId = switch (operation) {
            case CREATE_INTENT, UPLOAD, STATUS, WITHDRAW -> authorizeCollaborate(caller, target);
            case DOWNLOAD -> authorizeRead(caller, target);
            case PUBLISH, PARENT_LIFECYCLE -> throw new IllegalArgumentException(
                    "worker attachment target lock requires its dedicated internal coordinator");
        };
        if (!tenantRepository.lockForTenantChanges(tenantId)) {
            throw new IllegalStateException("attachment target tenant is unavailable");
        }
        WorkTask task = workTaskRepository.findVisibleByIdForUpdate(tenantId,
                new WorkTaskId(target.id()), new MemberId(caller.actorId()),
                caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE)).orElseThrow(
                WorkTaskAttachmentTargetAccess::targetNotFound);
        return new AttachmentTargetLifecycle(tenantId,
                task.getStatus() != io.github.archetom.crm.domain.model.WorkTaskStatus.DELETED);
    }

    @Override
    public AttachmentTargetLifecycle lockForWorker(TenantId tenantId, AttachmentTarget target) {
        requireTarget(target);
        if (tenantId == null || !tenantRepository.lockForTenantChanges(tenantId)) {
            throw new IllegalStateException("attachment target tenant is unavailable");
        }
        WorkTask task = workTaskRepository.findByIdForAttachmentMutation(tenantId,
                new WorkTaskId(target.id())).orElseThrow(
                () -> new IllegalArgumentException("attachment target is not found"));
        return new AttachmentTargetLifecycle(tenantId,
                task.getStatus() != io.github.archetom.crm.domain.model.WorkTaskStatus.DELETED);
    }

    private TenantId visible(AuthenticatedCaller caller, AttachmentTarget target,
                             String authority, boolean forUpdate) {
        requireTarget(target);
        TenantId tenantId = organizationAccessGuard.requireActiveMembership(caller, authority);
        MemberId memberId = new MemberId(caller.actorId());
        boolean manager = caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE);
        var result = forUpdate
                ? workTaskRepository.findVisibleByIdForUpdate(tenantId, new WorkTaskId(target.id()),
                memberId, manager)
                : workTaskRepository.findVisibleById(tenantId, new WorkTaskId(target.id()), memberId, manager);
        if (result.isEmpty()) {
            throw targetNotFound();
        }
        return tenantId;
    }

    private static void requireTarget(AttachmentTarget target) {
        if (target == null || target.type() != AttachmentTargetType.WORK_TASK) {
            throw new IllegalArgumentException("attachment target is unsupported");
        }
    }

    private static NonRetryableApplicationException targetNotFound() {
        return new NonRetryableApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                "Requested resource was not found");
    }
}
