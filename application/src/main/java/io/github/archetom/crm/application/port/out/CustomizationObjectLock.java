package io.github.archetom.crm.application.port.out;

import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.TenantId;

/** Serializes customization catalog and dependent writes for one tenant object. */
public interface CustomizationObjectLock {

    /** Acquires the database row lock for the current transaction. */
    void acquire(TenantId tenantId, BusinessObjectType objectType);
}
