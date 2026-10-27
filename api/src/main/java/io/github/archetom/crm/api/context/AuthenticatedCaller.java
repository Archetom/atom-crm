package io.github.archetom.crm.api.context;

import java.util.Set;

/**
 * Identity and tenant established by a trusted authentication mechanism.
 *
 * <p>This type is server-side context. It must never be populated from a
 * request body or from untrusted client-supplied role headers.</p>
 */
public record AuthenticatedCaller(Long actorId, Long tenantId, Set<String> authorities) {

    public AuthenticatedCaller {
        if (actorId == null || actorId <= 0) {
            throw new IllegalArgumentException("actorId must be positive");
        }
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
    }

    public boolean hasAuthority(String authority) {
        return authorities.contains(authority);
    }
}
