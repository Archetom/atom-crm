package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.application.security.ConnectorCredential;
import io.github.archetom.crm.domain.entity.ConnectorRegistration;
import io.github.archetom.crm.domain.model.ConnectorStatus;
import io.github.archetom.crm.domain.valueobject.ConnectorId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConnectorRegistrationPOConverterTest {
    private final ConnectorRegistrationPOConverter converter =
            new ConnectorRegistrationPOConverter();

    @Test
    void rootAndHashOnlyCredentialRoundTripWithoutEvents() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 19, 12, 0);
        ConnectorCredential.Generated generated = ConnectorCredential.create();
        ConnectorRegistration value = ConnectorRegistration.create(
                new TenantId(9L), generated.connectorRef(), generated.keyId(),
                generated.verifier(), now);
        value.onPersisted(new ConnectorId(4L), 3L, now, now);

        var root = converter.toPO(value);
        var credential = converter.toPO(value.getTenantId(), value.getId(), value.getConnectorRef(),
                value.getCredentialKeys().getFirst());
        ConnectorRegistration restored = converter.toDomain(root, List.of(credential));

        assertEquals(ConnectorStatus.ACTIVE, restored.getStatus());
        assertEquals(generated.connectorRef(), restored.getConnectorRef());
        assertEquals(generated.connectorRef().getValue(), credential.getConnectorRef());
        assertEquals(generated.verifier(),
                restored.getCredentialKeys().getFirst().getSecretVerifier());
        assertFalse(credential.toString().contains(generated.verifier()));
        assertEquals(3L, restored.getVersion());
        assertFalse(restored.hasDomainEvents());
    }
}
