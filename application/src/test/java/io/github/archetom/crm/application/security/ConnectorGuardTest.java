package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedConnector;
import io.github.archetom.crm.application.port.out.ConnectorCredentialLookup;
import io.github.archetom.crm.application.port.out.ConnectorIdentityLookup;
import io.github.archetom.crm.application.service.ConnectorAuthenticationService;
import io.github.archetom.crm.domain.model.ConnectorCredentialStatus;
import io.github.archetom.crm.domain.model.ConnectorStatus;
import io.github.archetom.crm.domain.repository.ConnectorRegistrationRepository;
import io.github.archetom.crm.domain.valueobject.ConnectorId;
import io.github.archetom.crm.domain.valueobject.ConnectorRef;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.ApplicationException;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConnectorGuardTest {

    @Test
    void resolvesTenantOnlyFromTheExactCurrentActiveIdentityProjection() {
        AuthenticationFixture fixture = fixture();
        ConnectorRef ref = fixture.ref();
        ConnectorIdentityLookup lookup = mock(ConnectorIdentityLookup.class);
        ConnectorRegistrationRepository repository = mock(ConnectorRegistrationRepository.class);
        when(lookup.findExact(ref)).thenReturn(Optional.of(new ConnectorIdentityLookup.ConnectorIdentity(
                new ConnectorId(4L), new TenantId(9L), ref, ConnectorStatus.ACTIVE, 3L)));
        ConnectorGuard guard = new ConnectorGuard(lookup, repository, fixture.service());

        ConnectorContext context = guard.requireActive(fixture.connector());

        assertEquals(new TenantId(9L), context.tenantId());
        assertEquals(new ConnectorId(4L), context.connectorId());
        verifyNoInteractions(repository);
    }

    @Test
    void disabledOrUnknownIdentityFailsBeforeAnyTenantRepositoryWork() {
        AuthenticationFixture fixture = fixture();
        ConnectorRef ref = fixture.ref();
        ConnectorIdentityLookup lookup = mock(ConnectorIdentityLookup.class);
        ConnectorRegistrationRepository repository = mock(ConnectorRegistrationRepository.class);
        when(lookup.findExact(ref)).thenReturn(Optional.of(new ConnectorIdentityLookup.ConnectorIdentity(
                new ConnectorId(4L), new TenantId(9L), ref, ConnectorStatus.DISABLED, 3L)));
        ConnectorGuard guard = new ConnectorGuard(lookup, repository, fixture.service());

        NonRetryableApplicationException error = assertThrows(
                NonRetryableApplicationException.class,
                () -> guard.requireActive(fixture.connector()));

        assertEquals(ApplicationErrorCode.AUTHENTICATION_REQUIRED, error.getErrorCode());
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsAContextNotIssuedByTheConfiguredAuthenticator() {
        AuthenticationFixture fixture = fixture();
        ConnectorIdentityLookup lookup = mock(ConnectorIdentityLookup.class);
        ConnectorRegistrationRepository repository = mock(ConnectorRegistrationRepository.class);
        ConnectorGuard guard = new ConnectorGuard(lookup, repository, fixture.service());

        NonRetryableApplicationException error = assertThrows(
                NonRetryableApplicationException.class,
                () -> guard.requireActive(new AuthenticatedConnector(
                        fixture.ref().getValue(), new Object())));

        assertEquals(ApplicationErrorCode.AUTHENTICATION_REQUIRED, error.getErrorCode());
        verifyNoInteractions(lookup, repository);
    }

    @Test
    void identityLookupFailureIsRetryableUnavailability() {
        AuthenticationFixture fixture = fixture();
        ConnectorIdentityLookup lookup = mock(ConnectorIdentityLookup.class);
        ConnectorRegistrationRepository repository = mock(ConnectorRegistrationRepository.class);
        when(lookup.findExact(fixture.ref())).thenThrow(new IllegalStateException("store unavailable"));
        ConnectorGuard guard = new ConnectorGuard(lookup, repository, fixture.service());

        ApplicationException error = assertThrows(
                ApplicationException.class, () -> guard.requireActive(fixture.connector()));

        assertEquals(ApplicationErrorCode.CONNECTOR_UNAVAILABLE, error.getErrorCode());
        verifyNoInteractions(repository);
    }

    @Test
    void registrationLockFailsBeforeRepositoryAccessWithoutATransaction() {
        AuthenticationFixture fixture = fixture();
        ConnectorIdentityLookup lookup = mock(ConnectorIdentityLookup.class);
        ConnectorRegistrationRepository repository = mock(ConnectorRegistrationRepository.class);
        ConnectorGuard guard = new ConnectorGuard(lookup, repository, fixture.service());
        ConnectorContext context = new ConnectorContext(
                new TenantId(9L), new ConnectorId(4L), fixture.ref(), 3L);

        assertThrows(IllegalStateException.class, () -> guard.lockActive(context));

        verifyNoInteractions(repository);
    }

    @Test
    void registrationLockRechecksTheResolvedIdentityInsideATransaction() {
        AuthenticationFixture fixture = fixture();
        ConnectorIdentityLookup lookup = mock(ConnectorIdentityLookup.class);
        ConnectorRegistrationRepository repository = mock(ConnectorRegistrationRepository.class);
        ConnectorGuard guard = new ConnectorGuard(lookup, repository, fixture.service());
        ConnectorContext context = new ConnectorContext(
                new TenantId(9L), new ConnectorId(4L), fixture.ref(), 3L);
        when(repository.lockActive(
                context.tenantId(), context.connectorId(), context.connectorRef())).thenReturn(true);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(context, guard.lockActive(context));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        verify(repository).lockActive(
                context.tenantId(), context.connectorId(), context.connectorRef());
    }

    private AuthenticationFixture fixture() {
        ConnectorCredential.Generated credential = ConnectorCredential.create();
        ConnectorCredentialLookup credentialLookup = (connectorRef, keyId) -> Optional.of(
                new ConnectorCredentialLookup.CredentialSecurityRecord(
                        credential.connectorRef(), credential.keyId(), credential.verifier(),
                        ConnectorStatus.ACTIVE, ConnectorCredentialStatus.ACTIVE));
        ConnectorAuthenticationService service = new ConnectorAuthenticationService(credentialLookup);
        AuthenticatedConnector connector = service.authenticate(credential.value())
                .orElseThrow().connector();
        return new AuthenticationFixture(credential.connectorRef(), connector, service);
    }

    private record AuthenticationFixture(
            ConnectorRef ref,
            AuthenticatedConnector connector,
            ConnectorAuthenticationService service) {
    }
}
