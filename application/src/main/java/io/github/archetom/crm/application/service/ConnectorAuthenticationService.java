package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedConnector;
import io.github.archetom.crm.application.port.out.ConnectorCredentialLookup;
import io.github.archetom.crm.application.security.ConnectorCredential;
import io.github.archetom.crm.domain.model.ConnectorCredentialStatus;
import io.github.archetom.crm.domain.model.ConnectorStatus;
import io.github.archetom.crm.domain.valueobject.ConnectorRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Verifies one connector credential without deriving or exposing tenant identity. */
@Service
@RequiredArgsConstructor
public class ConnectorAuthenticationService {
    private final ConnectorCredentialLookup credentialLookup;
    private final Object evidenceIssuer = new Object();

    public Optional<VerifiedConnector> authenticate(String credential) {
        var parsed = ConnectorCredential.parse(credential);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        var parts = parsed.get();
        var stored = credentialLookup.findExact(parts.connectorRef(), parts.keyId());
        String verifier = stored.map(ConnectorCredentialLookup.CredentialSecurityRecord::secretVerifier)
                .orElse(null);
        boolean secretMatches = ConnectorCredential.matches(parts.verifier(), verifier);
        if (!secretMatches || stored.isEmpty()) {
            return Optional.empty();
        }
        var record = stored.get();
        if (!record.connectorRef().sameValueAs(parts.connectorRef())
                || !record.keyId().equals(parts.keyId())
                || record.connectorStatus() != ConnectorStatus.ACTIVE
                || record.credentialStatus() != ConnectorCredentialStatus.ACTIVE) {
            return Optional.empty();
        }
        return Optional.of(new VerifiedConnector(new AuthenticatedConnector(
                record.connectorRef().getValue(),
                new AuthenticationEvidence(evidenceIssuer, record.connectorRef()))));
    }

    /** Rejects contexts directly constructed by another bean or another authenticator instance. */
    public boolean issued(AuthenticatedConnector connector) {
        if (connector == null
                || !(connector.authenticationEvidence() instanceof AuthenticationEvidence evidence)
                || evidence.issuer() != evidenceIssuer) {
            return false;
        }
        try {
            return evidence.connectorRef().sameValueAs(new ConnectorRef(connector.connectorRef()));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private record AuthenticationEvidence(Object issuer, ConnectorRef connectorRef) {
    }

    public record VerifiedConnector(AuthenticatedConnector connector) {
        public VerifiedConnector {
            if (connector == null) {
                throw new IllegalArgumentException("verified connector context is required");
            }
        }
    }
}
