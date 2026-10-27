package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.ConnectorRegistration;
import io.github.archetom.crm.domain.model.ConnectorQuery;
import io.github.archetom.crm.domain.valueobject.ConnectorId;
import io.github.archetom.crm.domain.valueobject.ConnectorRef;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Optional;

/** Human control-plane repository. Every operation is explicitly tenant scoped. */
public interface ConnectorRegistrationRepository {
    ConnectorRegistration save(TenantId tenantId, ConnectorRegistration registration);
    Optional<ConnectorRegistration> findById(TenantId tenantId, ConnectorId connectorId);
    Optional<ConnectorRegistration> findByIdForUpdate(TenantId tenantId, ConnectorId connectorId);
    PageResult<ConnectorRegistration> findPage(TenantId tenantId, ConnectorQuery query);

    /** Future ingress commands call this after early identity resolution and before their first write. */
    boolean lockActive(TenantId tenantId, ConnectorId connectorId, ConnectorRef connectorRef);
}
