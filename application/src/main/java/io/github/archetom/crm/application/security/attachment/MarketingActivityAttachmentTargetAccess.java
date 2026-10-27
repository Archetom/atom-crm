package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.domain.entity.MarketingActivity;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.MarketingActivityRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.valueobject.MarketingActivityId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Marketing activities are tenant-wide roots, so attachments use activity authorities only. */
@Component
@RequiredArgsConstructor
public class MarketingActivityAttachmentTargetAccess implements AttachmentTargetAccess {
    private final OrganizationAccessGuard accessGuard;
    private final OrganizationTenantRepository tenantRepository;
    private final MarketingActivityRepository activityRepository;

    @Override
    public AttachmentTargetType targetType() {
        return AttachmentTargetType.MARKETING_ACTIVITY;
    }

    @Override
    public TenantId authorizeRead(AuthenticatedCaller caller, AttachmentTarget target) {
        return visible(caller, target, AuthorityCatalog.MARKETING_ACTIVITIES_READ);
    }

    @Override
    public TenantId authorizeCollaborate(AuthenticatedCaller caller, AttachmentTarget target) {
        return visible(caller, target, AuthorityCatalog.MARKETING_ACTIVITIES_WRITE);
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
        return lock(tenantId, target);
    }

    @Override
    public AttachmentTargetLifecycle lockForWorker(TenantId tenantId, AttachmentTarget target) {
        requireTarget(target);
        return lock(tenantId, target);
    }

    private TenantId visible(AuthenticatedCaller caller, AttachmentTarget target, String authority) {
        requireTarget(target);
        TenantId tenantId = accessGuard.requireActiveMembership(caller, authority);
        activityRepository.findById(tenantId, id(target)).filter(MarketingActivity::isActive)
                .orElseThrow(MarketingActivityAttachmentTargetAccess::notFound);
        return tenantId;
    }

    private AttachmentTargetLifecycle lock(TenantId tenantId, AttachmentTarget target) {
        if (tenantId == null || !tenantRepository.lockForTenantChanges(tenantId)) {
            throw new IllegalStateException("attachment target tenant is unavailable");
        }
        MarketingActivity activity = activityRepository.findByIdForUpdate(tenantId, id(target))
                .orElseThrow(MarketingActivityAttachmentTargetAccess::notFound);
        return new AttachmentTargetLifecycle(tenantId, activity.isActive());
    }

    private static MarketingActivityId id(AttachmentTarget target) {
        return new MarketingActivityId(target.id());
    }

    private static void requireTarget(AttachmentTarget target) {
        if (target == null || target.type() != AttachmentTargetType.MARKETING_ACTIVITY) {
            throw new IllegalArgumentException("attachment target is unsupported");
        }
    }

    private static NonRetryableApplicationException notFound() {
        return new NonRetryableApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                "Requested resource was not found");
    }
}
