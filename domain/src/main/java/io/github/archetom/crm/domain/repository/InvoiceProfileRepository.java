package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.InvoiceProfile;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.InvoiceProfileId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Optional;

public interface InvoiceProfileRepository {
    InvoiceProfile save(TenantId tenantId, InvoiceProfile profile);

    Optional<InvoiceProfile> findById(
            TenantId tenantId, InvoiceProfileId profileId);

    List<InvoiceProfile> findByCustomer(
            TenantId tenantId, CustomerId customerId, int limit);

    long countActiveByCustomer(TenantId tenantId, CustomerId customerId);
}
