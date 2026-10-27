package io.github.archetom.crm.application.security;

import java.util.Set;

/** Resolves tenant business capabilities from verified actor and tenant identity. */
public interface TenantAuthorityResolver {

    Set<String> resolve(long actorId, long tenantId);
}
