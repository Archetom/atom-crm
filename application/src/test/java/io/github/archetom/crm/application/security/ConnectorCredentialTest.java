package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.valueobject.ConnectorRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorCredentialTest {

    @Test
    void generatedCredentialHasCanonicalOpaquePartsAndAHashOnlyVerifier() {
        ConnectorCredential.Generated generated = ConnectorCredential.create();
        var parsed = ConnectorCredential.parse(generated.value()).orElseThrow();

        assertEquals(generated.connectorRef(), parsed.connectorRef());
        assertEquals(generated.keyId(), parsed.keyId());
        assertEquals(64, generated.verifier().length());
        assertFalse(generated.value().contains(generated.verifier()));
        assertTrue(ConnectorCredential.matches(parsed.verifier(), generated.verifier()));
        assertFalse(generated.toString().contains(generated.secret()));
        assertFalse(parsed.toString().contains(parsed.secret()));
    }

    @Test
    void malformedOrNonCanonicalValuesNeverSelectAConnector() {
        ConnectorCredential.Generated generated = ConnectorCredential.create();
        assertTrue(ConnectorCredential.parse(generated.value() + "=").isEmpty());
        assertTrue(ConnectorCredential.parse("tenant.key.secret").isEmpty());
    }

    @Test
    void rotationKeepsTheExactConnectorReference() {
        ConnectorRef reference = ConnectorCredential.create().connectorRef();
        assertEquals(reference, ConnectorCredential.rotate(reference).connectorRef());
    }
}
