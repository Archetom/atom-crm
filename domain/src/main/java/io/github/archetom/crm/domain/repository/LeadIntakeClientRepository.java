package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.LeadIntakeClient;
import io.github.archetom.crm.domain.valueobject.LeadIntakeClientId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Optional;

public interface LeadIntakeClientRepository {
    LeadIntakeClient save(TenantId tenantId, LeadIntakeClient client);
    List<LeadIntakeClient> findAll(TenantId tenantId);
    Optional<LeadIntakeClient> findById(TenantId tenantId, LeadIntakeClientId clientId);
    Optional<LeadIntakeClient> findActiveByKeyId(TenantId tenantId, String keyId);
}
