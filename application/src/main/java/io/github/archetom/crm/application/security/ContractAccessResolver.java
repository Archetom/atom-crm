package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.ContractVisibility;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves one authority's data scope into a database-ready contract predicate. */
@Component
@RequiredArgsConstructor
public class ContractAccessResolver {

    private final DataScopeVisibilityResolver scopeResolver;

    public ContractAccess access(
            AuthenticatedCaller caller, String authority, ContractAccessMode mode) {
        DataScopeVisibilityResolver.ResolvedScope scope =
                scopeResolver.resolve(caller, authority, "Contract");
        return new ContractAccess(scope.tenantId(), scope.memberId(), new ContractVisibility(
                scope.dataScope(), scope.memberId(), scope.departmentIds(), mode));
    }

    public record ContractAccess(
            TenantId tenantId, MemberId memberId, ContractVisibility visibility) {
    }
}
