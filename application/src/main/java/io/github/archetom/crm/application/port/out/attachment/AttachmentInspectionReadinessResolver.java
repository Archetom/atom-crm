package io.github.archetom.crm.application.port.out.attachment;

import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.Instant;
import java.util.Set;

public interface AttachmentInspectionReadinessResolver {
    Snapshot resolve(TenantId tenantId);

    record Snapshot(AttachmentReadiness readiness, Set<String> allowedMediaTypes,
                    Instant expiresAt, String definitionGeneration, String safeReason) {
        public Snapshot(AttachmentReadiness readiness, Set<String> allowedMediaTypes,
                        Instant expiresAt, String safeReason) {
            this(readiness, allowedMediaTypes, expiresAt, null, safeReason);
        }
        public boolean allows(String mediaType, Instant now) {
            return readiness == AttachmentReadiness.READY && mediaType != null
                    && allowedMediaTypes != null && allowedMediaTypes.contains(mediaType)
                    && expiresAt != null && now != null && expiresAt.isAfter(now);
        }
    }
}
