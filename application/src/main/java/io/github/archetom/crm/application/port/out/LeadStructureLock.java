package io.github.archetom.crm.application.port.out;

import io.github.archetom.crm.domain.valueobject.TenantId;

/** Serializes lead hierarchy changes within one tenant. */
public interface LeadStructureLock {
    void acquire(TenantId tenantId);
}
