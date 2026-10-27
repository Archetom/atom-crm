package io.github.archetom.crm.application.port.out;

import io.github.archetom.crm.domain.valueobject.ConnectorRef;

/** Clears local admission state after credential or registration lifecycle changes commit. */
public interface ConnectorAuthenticationStateInvalidator {
    void invalidateCredential(ConnectorRef connectorRef, String keyId);
    void invalidateConnector(ConnectorRef connectorRef);
}
