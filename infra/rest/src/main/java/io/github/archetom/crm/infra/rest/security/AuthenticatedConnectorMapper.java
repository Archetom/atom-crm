package io.github.archetom.crm.infra.rest.security;

import io.github.archetom.crm.api.context.AuthenticatedConnector;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Maps only the dedicated infrastructure connector principal to the API context. */
@Component
public class AuthenticatedConnectorMapper {
    public AuthenticatedConnector from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof ConnectorPrincipal principal)) {
            throw new AccessDeniedException("Authenticated connector context is required");
        }
        return principal.connector();
    }
}
