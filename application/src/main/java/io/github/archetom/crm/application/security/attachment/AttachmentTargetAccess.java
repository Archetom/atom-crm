package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.valueobject.TenantId;

/** Target-specific authorization and lock boundary for B24. */
public interface AttachmentTargetAccess {
    AttachmentTargetType targetType();
    TenantId authorizeRead(AuthenticatedCaller caller, AttachmentTarget target);
    TenantId authorizeCollaborate(AuthenticatedCaller caller, AttachmentTarget target);
    AttachmentTargetLifecycle lockForAttachmentMutation(
            AuthenticatedCaller caller, AttachmentTarget target, AttachmentTargetOperation operation);
    AttachmentTargetLifecycle lockForWorker(TenantId tenantId, AttachmentTarget target);
}
