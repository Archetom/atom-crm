package io.github.archetom.crm.infra.rest.security;

import io.github.archetom.crm.api.context.AuthenticatedConnector;
import io.github.archetom.crm.domain.valueobject.ConnectorRef;

import java.security.Principal;

/** Infrastructure-only non-human principal established by connector bearer authentication. */
public record ConnectorPrincipal(AuthenticatedConnector connector) implements Principal {
    public ConnectorPrincipal {
        if (connector == null || !ConnectorRef.isCanonical(connector.connectorRef())) {
            throw new IllegalArgumentException("connector principal reference is invalid");
        }
    }

    public String connectorRef() {
        return connector.connectorRef();
    }

    @Override
    public String getName() {
        return connectorRef();
    }
}
