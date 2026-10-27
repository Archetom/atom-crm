package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.valueobject.TenantId;

/** Common delegation surface; every concrete B24b target remains an explicit Spring registration. */
public abstract class CrmAttachmentTargetAccess implements AttachmentTargetAccess {
    private final CrmAttachmentTargetAccessSupport support;
    private final AttachmentTargetType type;

    protected CrmAttachmentTargetAccess(CrmAttachmentTargetAccessSupport support, AttachmentTargetType type) {
        this.support = support;
        this.type = type;
    }

    @Override public final AttachmentTargetType targetType() { return type; }
    @Override public final TenantId authorizeRead(AuthenticatedCaller caller, AttachmentTarget target) {
        requireType(target); return support.authorizeRead(caller, target);
    }
    @Override public final TenantId authorizeCollaborate(AuthenticatedCaller caller, AttachmentTarget target) {
        requireType(target); return support.authorizeCollaborate(caller, target);
    }
    @Override public final AttachmentTargetLifecycle lockForAttachmentMutation(
            AuthenticatedCaller caller, AttachmentTarget target, AttachmentTargetOperation operation) {
        requireType(target); return support.lockForAttachmentMutation(caller, target, operation);
    }
    @Override public final AttachmentTargetLifecycle lockForWorker(TenantId tenantId, AttachmentTarget target) {
        requireType(target); return support.lockForWorker(tenantId, target);
    }

    private void requireType(AttachmentTarget target) {
        if (target == null || target.type() != type) {
            throw new IllegalArgumentException("attachment target is unsupported");
        }
    }
}
