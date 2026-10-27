package io.github.archetom.crm.infra.rest.security;

import io.github.archetom.crm.api.context.AuthenticatedConnector;
import io.github.archetom.crm.application.security.ConnectorCredential;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticatedConnectorMapperTest {
    private final AuthenticatedConnectorMapper mapper = new AuthenticatedConnectorMapper();

    @Test
    void acceptsOnlyTheDedicatedConnectorPrincipal() {
        String reference = ConnectorCredential.create().connectorRef().getValue();
        AuthenticatedConnector connector = new AuthenticatedConnector(reference, new Object());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                new ConnectorPrincipal(connector), null, List.of());

        assertEquals(reference, mapper.from(authentication).connectorRef());
        assertSame(connector, mapper.from(authentication));
        assertThrows(AccessDeniedException.class, () -> mapper.from(
                UsernamePasswordAuthenticationToken.authenticated(
                        new ActorPrincipal(7L, 9L), null, List.of())));
        assertThrows(AccessDeniedException.class, () -> mapper.from(null));
    }
}
