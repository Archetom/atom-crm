package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedConnector;
import io.github.archetom.crm.application.port.out.ConnectorIdentityLookup;
import io.github.archetom.crm.application.service.ConnectorAuthenticationService;
import io.github.archetom.crm.domain.model.ConnectorStatus;
import io.github.archetom.crm.domain.repository.ConnectorRegistrationRepository;
import io.github.archetom.crm.domain.valueobject.ConnectorRef;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.ApplicationException;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Resolves a connector subject to its current active one-tenant registration. */
@Component
@RequiredArgsConstructor
public class ConnectorGuard {
    private final ConnectorIdentityLookup identityLookup;
    private final ConnectorRegistrationRepository registrationRepository;
    private final ConnectorAuthenticationService authenticationService;

    public ConnectorContext requireActive(AuthenticatedConnector connector) {
        ConnectorRef connectorRef;
        try {
            connectorRef = connector == null || !authenticationService.issued(connector)
                    ? null : new ConnectorRef(connector.connectorRef());
        } catch (RuntimeException exception) {
            throw authenticationRequired();
        }
        if (connectorRef == null) {
            throw authenticationRequired();
        }
        ConnectorIdentityLookup.ConnectorIdentity identity;
        try {
            identity = identityLookup.findExact(connectorRef)
                .filter(value -> value.connectorRef().sameValueAs(connectorRef))
                .filter(value -> value.status() == ConnectorStatus.ACTIVE)
                .orElseThrow(this::authenticationRequired);
        } catch (NonRetryableApplicationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApplicationException(
                    ApplicationErrorCode.CONNECTOR_UNAVAILABLE, exception);
        }
        return new ConnectorContext(identity.tenantId(), identity.connectorId(),
                identity.connectorRef(), identity.version());
    }

    /** Must run inside an ingress write transaction before the first business write. */
    public ConnectorContext lockActive(ConnectorContext context) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Connector registration locking requires an active transaction");
        }
        if (context == null || !registrationRepository.lockActive(
                context.tenantId(), context.connectorId(), context.connectorRef())) {
            throw authenticationRequired();
        }
        return context;
    }

    private NonRetryableApplicationException authenticationRequired() {
        return new NonRetryableApplicationException(
                ApplicationErrorCode.AUTHENTICATION_REQUIRED,
                "Active connector authentication is required");
    }
}
