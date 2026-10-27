package io.github.archetom.crm.infra.rest.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Maps an infrastructure authentication object to the explicit API context.
 */
@Component
public class AuthenticatedCallerMapper {

    public AuthenticatedCaller from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new AccessDeniedException("Authenticated actor context is required");
        }

        return new AuthenticatedCaller(
                principal.userId(),
                principal.tenantId(),
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toUnmodifiableSet()));
    }
}
