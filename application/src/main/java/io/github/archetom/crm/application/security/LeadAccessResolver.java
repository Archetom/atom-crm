package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.model.LeadAccessMode;
import io.github.archetom.crm.domain.model.LeadVisibility;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves one authority's data scope into a database-ready lead predicate. */
@Component
@RequiredArgsConstructor
public class LeadAccessResolver {

    private final DataScopeVisibilityResolver scopeResolver;

    public LeadAccess access(
            AuthenticatedCaller caller, String authority, LeadAccessMode accessMode) {
        DataScopeVisibilityResolver.ResolvedScope scope =
                scopeResolver.resolve(caller, authority, "Lead");
        return new LeadAccess(scope.tenantId(), scope.memberId(), new LeadVisibility(
                scope.dataScope(), scope.memberId(), scope.departmentIds(), accessMode));
    }

    public record LeadAccess(
            TenantId tenantId, MemberId memberId, LeadVisibility visibility) {
    }
}
