package io.github.archetom.crm.application.port.out;

import io.github.archetom.crm.domain.valueobject.TenantId;

/** Serializes category-tree validation and catalog dependency changes per tenant. */
public interface ProductCatalogLock {
    void acquire(TenantId tenantId);
}
