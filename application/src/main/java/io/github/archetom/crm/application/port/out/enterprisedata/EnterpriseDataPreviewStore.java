package io.github.archetom.crm.application.port.out.enterprisedata;

import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.Instant;
import java.util.Optional;

/** Short-lived opaque preview-reference storage; successful claim consumes the reference. */
public interface EnterpriseDataPreviewStore {
    String issue(EnterpriseDataPreview preview);

    /**
     * Reads a reference without consuming it, so a caller can validate against it before it has
     * changed anything.
     *
     * <p>Consuming during validation would spend the reference on attempts that go on to fail and
     * roll back: this store is process-local and outside the database transaction, so nothing
     * restores it. The caller would then be told to redo the whole preview even though no data was
     * adopted. Read here, and {@link #claim} once the work has actually succeeded.</p>
     */
    Optional<EnterpriseDataPreview> peek(TenantId tenantId, MemberId memberId,
                                         String reference, Instant now);

    /** Consumes the reference. A second call for the same reference finds nothing. */
    Optional<EnterpriseDataPreview> claim(TenantId tenantId, MemberId memberId,
                                           String reference, Instant now);
}
