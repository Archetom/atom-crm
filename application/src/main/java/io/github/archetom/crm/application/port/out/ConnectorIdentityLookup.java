package io.github.archetom.crm.application.port.out;

import io.github.archetom.crm.domain.model.ConnectorStatus;
import io.github.archetom.crm.domain.valueobject.ConnectorId;
import io.github.archetom.crm.domain.valueobject.ConnectorRef;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Optional;

/** Exact, verifier-free pre-tenant lookup used only by the application connector guard. */
public interface ConnectorIdentityLookup {
    Optional<ConnectorIdentity> findExact(ConnectorRef connectorRef);

    record ConnectorIdentity(
            ConnectorId connectorId, TenantId tenantId, ConnectorRef connectorRef,
            ConnectorStatus status, Long version) {
    }
}
