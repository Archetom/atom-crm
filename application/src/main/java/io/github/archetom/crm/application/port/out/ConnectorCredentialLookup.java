package io.github.archetom.crm.application.port.out;

import io.github.archetom.crm.domain.model.ConnectorCredentialStatus;
import io.github.archetom.crm.domain.model.ConnectorStatus;
import io.github.archetom.crm.domain.valueobject.ConnectorRef;

import java.util.Optional;

/** Exact, single-row pre-tenant lookup used only by transport authentication. */
public interface ConnectorCredentialLookup {
    Optional<CredentialSecurityRecord> findExact(ConnectorRef connectorRef, String keyId);

    record CredentialSecurityRecord(
            ConnectorRef connectorRef, String keyId, String secretVerifier,
            ConnectorStatus connectorStatus, ConnectorCredentialStatus credentialStatus) {
        @Override
        public String toString() {
            return "ConnectorCredentialLookup.CredentialSecurityRecord[REDACTED]";
        }
    }
}
