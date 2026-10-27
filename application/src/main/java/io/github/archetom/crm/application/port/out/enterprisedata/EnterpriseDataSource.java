package io.github.archetom.crm.application.port.out.enterprisedata;

import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Optional;

/** Tenant-bound outbound port implemented by one governed enterprise-data adapter. */
public interface EnterpriseDataSource {

    EnterpriseDataCapabilitySnapshot capabilities(TenantId tenantId);

    List<EnterpriseDataObservation> searchByRegisteredName(
            TenantId tenantId, EnterpriseNameSearchQuery query);

    Optional<EnterpriseDataObservation> findByRegisteredIdentifier(
            TenantId tenantId, RegisteredIdentifierKey identifier);
}
