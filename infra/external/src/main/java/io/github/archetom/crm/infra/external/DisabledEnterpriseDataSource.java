package io.github.archetom.crm.infra.external;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataCapabilitySnapshot;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataObservation;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSource;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceError;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceException;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseNameSearchQuery;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Optional;

/** Secure default used when no governed enterprise-data adapter is installed. */
public final class DisabledEnterpriseDataSource implements EnterpriseDataSource {

    @Override
    public EnterpriseDataCapabilitySnapshot capabilities(TenantId tenantId) {
        requireTenant(tenantId);
        return EnterpriseDataCapabilitySnapshot.unavailable();
    }

    @Override
    public List<EnterpriseDataObservation> searchByRegisteredName(
            TenantId tenantId, EnterpriseNameSearchQuery query) {
        requireTenant(tenantId);
        throw unavailable();
    }

    @Override
    public Optional<EnterpriseDataObservation> findByRegisteredIdentifier(
            TenantId tenantId, RegisteredIdentifierKey identifier) {
        requireTenant(tenantId);
        throw unavailable();
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant must not be null");
        }
    }

    private EnterpriseDataSourceException unavailable() {
        return new EnterpriseDataSourceException(EnterpriseDataSourceError.UNAVAILABLE);
    }
}
