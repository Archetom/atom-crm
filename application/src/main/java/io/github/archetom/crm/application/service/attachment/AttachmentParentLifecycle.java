package io.github.archetom.crm.application.service.attachment;

import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.valueobject.TenantId;

/** Internal parent-lifecycle coordinator. It only closes local relations and schedules deletion work. */
public interface AttachmentParentLifecycle {
    void withdrawAll(TenantId tenantId, AttachmentTarget target);

    /**
     * Terminal parent cutoff: remove every current relation that cannot be read yet while the
     * caller holds the parent lifecycle lock. Available relations remain the frozen set.
     */
    void cutoffNonAvailable(TenantId tenantId, AttachmentTarget target);

    /** Re-arms deferred inspection/publication work after a pool target becomes active again. */
    void resumeAll(TenantId tenantId, AttachmentTarget target);
}
