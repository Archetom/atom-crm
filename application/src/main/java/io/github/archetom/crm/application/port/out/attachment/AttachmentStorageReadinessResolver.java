package io.github.archetom.crm.application.port.out.attachment;

import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.Instant;
import java.util.Set;

public interface AttachmentStorageReadinessResolver {
    Snapshot resolve(TenantId tenantId, AttachmentOperation operation);

    record Snapshot(AttachmentReadiness readiness, StorageBindingRef binding,
                    Set<AttachmentOperation> readyOperations, Instant expiresAt, String safeReason) {
        public boolean isReady(AttachmentOperation operation, Instant now) {
            return readiness == AttachmentReadiness.READY && binding != null
                    && readyOperations != null && readyOperations.contains(operation)
                    && expiresAt != null && now != null && expiresAt.isAfter(now);
        }
    }
}
