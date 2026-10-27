package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedConnector;
import io.github.archetom.crm.application.port.out.ConnectorCredentialLookup;
import io.github.archetom.crm.application.security.ConnectorCredential;
import io.github.archetom.crm.domain.model.ConnectorCredentialStatus;
import io.github.archetom.crm.domain.model.ConnectorStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectorAuthenticationServiceTest {

    @Test
    void verifiesOnlyAnActiveExactConnectorAndNeverReturnsTenantOrVerifier() {
        ConnectorCredential.Generated credential = ConnectorCredential.create();
        ConnectorCredentialLookup lookup = mock(ConnectorCredentialLookup.class);
        var securityRecord = new ConnectorCredentialLookup.CredentialSecurityRecord(
                credential.connectorRef(), credential.keyId(), credential.verifier(),
                ConnectorStatus.ACTIVE, ConnectorCredentialStatus.ACTIVE);
        when(lookup.findExact(credential.connectorRef(), credential.keyId()))
                .thenReturn(Optional.of(securityRecord));

        ConnectorAuthenticationService service = new ConnectorAuthenticationService(lookup);
        var verified = service.authenticate(credential.value()).orElseThrow();

        assertEquals(credential.connectorRef().getValue(), verified.connector().connectorRef());
        assertTrue(service.issued(verified.connector()));
        assertFalse(securityRecord.toString().contains(credential.verifier()));
        verify(lookup).findExact(credential.connectorRef(), credential.keyId());
    }

    @Test
    void wrongSecretAndDisabledRegistrationHaveTheSameEmptyResult() {
        ConnectorCredential.Generated credential = ConnectorCredential.create();
        ConnectorCredentialLookup lookup = mock(ConnectorCredentialLookup.class);
        when(lookup.findExact(credential.connectorRef(), credential.keyId())).thenReturn(Optional.of(
                new ConnectorCredentialLookup.CredentialSecurityRecord(
                        credential.connectorRef(), credential.keyId(), credential.verifier(),
                        ConnectorStatus.DISABLED, ConnectorCredentialStatus.ACTIVE)));
        ConnectorAuthenticationService service = new ConnectorAuthenticationService(lookup);

        assertTrue(service.authenticate(credential.value()).isEmpty());
        ConnectorCredential.Generated other = ConnectorCredential.rotate(credential.connectorRef());
        when(lookup.findExact(other.connectorRef(), other.keyId())).thenReturn(Optional.empty());
        assertTrue(service.authenticate(other.value()).isEmpty());
    }

    @Test
    void rejectsAContextNotIssuedByTheSameAuthenticatorInstance() {
        ConnectorCredential.Generated credential = ConnectorCredential.create();
        ConnectorCredentialLookup lookup = valueLookup(credential);
        ConnectorAuthenticationService issuer = new ConnectorAuthenticationService(lookup);
        ConnectorAuthenticationService other = new ConnectorAuthenticationService(lookup);
        AuthenticatedConnector issued = issuer.authenticate(credential.value())
                .orElseThrow().connector();

        assertTrue(issuer.issued(issued));
        assertFalse(other.issued(issued));
        assertFalse(issuer.issued(new AuthenticatedConnector(
                credential.connectorRef().getValue(), new Object())));
    }

    private ConnectorCredentialLookup valueLookup(ConnectorCredential.Generated credential) {
        return (connectorRef, keyId) -> Optional.of(
                new ConnectorCredentialLookup.CredentialSecurityRecord(
                        credential.connectorRef(), credential.keyId(), credential.verifier(),
                        ConnectorStatus.ACTIVE, ConnectorCredentialStatus.ACTIVE));
    }
}
