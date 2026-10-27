package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves one authority's data scope into a database-ready customer predicate. */
@Component
@RequiredArgsConstructor
public class CustomerAccessResolver {

    private final DataScopeVisibilityResolver scopeResolver;

    public CustomerAccess access(
            AuthenticatedCaller caller, String authority, CustomerAccessMode accessMode) {
        DataScopeVisibilityResolver.ResolvedScope scope =
                scopeResolver.resolve(caller, authority, "Customer");
        return new CustomerAccess(scope.tenantId(), scope.memberId(), new CustomerVisibility(
                scope.dataScope(), scope.memberId(), scope.departmentIds(), accessMode));
    }

    public record CustomerAccess(
            TenantId tenantId, MemberId memberId, CustomerVisibility visibility) {
    }
}
