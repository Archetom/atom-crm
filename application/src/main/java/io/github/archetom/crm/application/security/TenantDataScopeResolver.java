package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.model.DataScope;

import java.util.Optional;

/** Resolves the verified caller's effective data scope for one scoped authority. */
public interface TenantDataScopeResolver {

    Optional<DataScope> resolve(AuthenticatedCaller caller, String authority);
}
