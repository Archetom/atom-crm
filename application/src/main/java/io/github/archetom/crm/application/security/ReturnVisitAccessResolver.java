package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.model.ReturnVisitAccessMode;
import io.github.archetom.crm.domain.model.ReturnVisitVisibility;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves one authority's data scope into a database-ready return visit predicate. */
@Component
@RequiredArgsConstructor
public class ReturnVisitAccessResolver {

    private final DataScopeVisibilityResolver scopeResolver;

    public ReturnVisitAccess access(
            AuthenticatedCaller caller, String authority, ReturnVisitAccessMode mode) {
        DataScopeVisibilityResolver.ResolvedScope scope =
                scopeResolver.resolve(caller, authority, "Return visit");
        return new ReturnVisitAccess(scope.tenantId(), scope.memberId(), new ReturnVisitVisibility(
                scope.dataScope(), scope.memberId(), scope.departmentIds(), mode));
    }

    public record ReturnVisitAccess(
            TenantId tenantId, MemberId memberId, ReturnVisitVisibility visibility) {
    }
}
