package io.github.archetom.crm.application.port.out;

import io.github.archetom.crm.domain.valueobject.TenantId;

/** Serializes contact hierarchy changes within one tenant. */
public interface ContactStructureLock {
    void acquire(TenantId tenantId);
}
