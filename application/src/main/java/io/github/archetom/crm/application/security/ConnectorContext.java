package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.valueobject.ConnectorId;
import io.github.archetom.crm.domain.valueobject.ConnectorRef;
import io.github.archetom.crm.domain.valueobject.TenantId;

/** Per-use-case tenant/connector scope resolved from the current active registration. */
public record ConnectorContext(
        TenantId tenantId, ConnectorId connectorId, ConnectorRef connectorRef, Long version) {

    public ConnectorContext {
        if (tenantId == null || connectorId == null || connectorRef == null
                || version == null || version < 0) {
            throw new IllegalArgumentException("resolved connector context is invalid");
        }
    }
}
