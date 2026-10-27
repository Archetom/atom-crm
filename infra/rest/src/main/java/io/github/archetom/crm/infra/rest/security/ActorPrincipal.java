package io.github.archetom.crm.infra.rest.security;

import java.security.Principal;

/**
 * Authenticated actor created by the explicitly enabled trusted-header adapter.
 *
 * <p>This principal is an infrastructure concern. Domain and persistence code
 * must not read it through a global or thread-local holder.</p>
 *
 * @param userId authenticated user ID
 * @param tenantId authenticated tenant ID
 */
public record ActorPrincipal(long userId, long tenantId) implements Principal {

    public ActorPrincipal {
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }
        if (tenantId <= 0) {
            throw new IllegalArgumentException("Tenant ID must be positive");
        }
    }

    @Override
    public String getName() {
        return Long.toString(userId);
    }
}
