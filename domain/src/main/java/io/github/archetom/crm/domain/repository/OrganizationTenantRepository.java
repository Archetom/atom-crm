package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

/** Atomic initialization marker for tenants that can accept local memberships. */
public interface OrganizationTenantRepository {

    boolean initialize(TenantId tenantId, MemberId initializedBy);

    boolean exists(TenantId tenantId);

    /**
     * Locks the initialized tenant row for the current transaction so every
     * department-tree mutation for that tenant follows one serialization point.
     */
    boolean lockForStructureChanges(TenantId tenantId);

    /** Serializes authorization and other tenant-wide invariant changes. */
    default boolean lockForTenantChanges(TenantId tenantId) {
        return lockForStructureChanges(tenantId);
    }
}
