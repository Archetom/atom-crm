package io.github.archetom.crm.application.port.out;

import io.github.archetom.crm.domain.valueobject.TenantId;

/** Serializes customer hierarchy changes within one tenant. */
public interface CustomerStructureLock {
    void acquire(TenantId tenantId);
}
