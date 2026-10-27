package io.github.archetom.crm.application.port.out;

import io.github.archetom.crm.domain.valueobject.TenantId;

/** Serializes tenant Opportunity sales-catalog lifecycle and pair-assignment checks. */
public interface OpportunityPipelineCatalogLock {
    void acquire(TenantId tenantId);
}
