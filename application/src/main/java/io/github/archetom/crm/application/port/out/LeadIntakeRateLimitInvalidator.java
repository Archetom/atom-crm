package io.github.archetom.crm.application.port.out;

import io.github.archetom.crm.domain.valueobject.TenantId;

/** Invalidates local admission state for a deactivated lead-intake credential. */
public interface LeadIntakeRateLimitInvalidator {

    void invalidate(TenantId tenantId, String keyId);
}
