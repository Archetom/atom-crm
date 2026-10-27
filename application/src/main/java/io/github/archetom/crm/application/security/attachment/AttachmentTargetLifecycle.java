package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.domain.valueobject.TenantId;

/** Re-read under the owning aggregate root lock; no CRM relation is exposed here. */
public record AttachmentTargetLifecycle(TenantId tenantId, boolean attachmentsPermitted,
                                        boolean attachmentsPaused) {
    public AttachmentTargetLifecycle(TenantId tenantId, boolean attachmentsPermitted) {
        this(tenantId, attachmentsPermitted, false);
    }

    public AttachmentTargetLifecycle {
        if (tenantId == null) {
            throw new IllegalArgumentException("attachment target tenant is required");
        }
        if (attachmentsPermitted && attachmentsPaused) {
            throw new IllegalArgumentException("permitted attachment target cannot be paused");
        }
    }
}
