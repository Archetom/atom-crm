package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ConnectorDomainException;
import io.github.archetom.crm.domain.model.ConnectorStatus;
import io.github.archetom.crm.domain.valueobject.ConnectorId;
import io.github.archetom.crm.domain.valueobject.ConnectorRef;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorRegistrationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 19, 12, 0);
    private static final String VERIFIER = "a".repeat(64);

    @Test
    void rotationIsBoundedAndRevocationAllowsAReplacementWithoutChangingIdentity() {
        ConnectorRegistration connector = create();
        connector.rotateCredential("B".repeat(24), "b".repeat(64), NOW.plusMinutes(1));

        assertEquals(2, connector.activeCredentialCount());
        assertThrows(ConnectorDomainException.class, () -> connector.rotateCredential(
                "C".repeat(24), "c".repeat(64), NOW.plusMinutes(2)));

        connector.revokeCredential("A".repeat(24), NOW.plusMinutes(3));
        connector.rotateCredential("C".repeat(24), "c".repeat(64), NOW.plusMinutes(4));

        assertEquals(2, connector.activeCredentialCount());
        assertEquals(ref(), connector.getConnectorRef());
        assertEquals(new TenantId(9L), connector.getTenantId());
    }

    @Test
    void disableIsRecoverableButRetirementIsIrreversibleAndRevokesEveryKey() {
        ConnectorRegistration connector = create();
        connector.disable(NOW.plusMinutes(1));
        assertEquals(ConnectorStatus.DISABLED, connector.getStatus());
        connector.activate(NOW.plusMinutes(2));
        assertEquals(ConnectorStatus.ACTIVE, connector.getStatus());

        connector.retire(NOW.plusMinutes(3));

        assertEquals(ConnectorStatus.RETIRED, connector.getStatus());
        assertEquals(0, connector.activeCredentialCount());
        assertTrue(connector.getCredentialKeys().stream().noneMatch(ConnectorCredentialKey::isActive));
        assertThrows(ConnectorDomainException.class,
                () -> connector.activate(NOW.plusMinutes(4)));
        assertThrows(ConnectorDomainException.class, () -> connector.rotateCredential(
                "B".repeat(24), "b".repeat(64), NOW.plusMinutes(4)));
    }

    @Test
    void reconstitutionRestoresVersionWithoutEventsOrTenantRebinding() {
        ConnectorRegistration connector = ConnectorRegistration.reconstitute(
                new ConnectorRegistration.Snapshot(new ConnectorId(3L), new TenantId(9L), ref(),
                        ConnectorStatus.ACTIVE, java.util.List.of(
                        new ConnectorRegistration.CredentialSnapshot("A".repeat(24), VERIFIER,
                                io.github.archetom.crm.domain.model.ConnectorCredentialStatus.ACTIVE,
                                NOW, null)), 7L, NOW, NOW.plusMinutes(1), null));

        assertEquals(7L, connector.getVersion());
        assertEquals(new TenantId(9L), connector.getTenantId());
        assertFalse(connector.hasDomainEvents());
    }

    private ConnectorRegistration create() {
        return ConnectorRegistration.create(
                new TenantId(9L), ref(), "A".repeat(24), VERIFIER, NOW);
    }

    private static ConnectorRef ref() {
        return new ConnectorRef(Base64.getUrlEncoder().withoutPadding()
                .encodeToString(new byte[32]));
    }
}
