package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.model.OpportunityVisibility;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves one authority's data scope into a database-ready opportunity predicate. */
@Component
@RequiredArgsConstructor
public class OpportunityAccessResolver {

    private final DataScopeVisibilityResolver scopeResolver;

    public OpportunityAccess access(
            AuthenticatedCaller caller, String authority, OpportunityAccessMode accessMode) {
        DataScopeVisibilityResolver.ResolvedScope scope =
                scopeResolver.resolve(caller, authority, "Opportunity");
        return new OpportunityAccess(scope.tenantId(), scope.memberId(), new OpportunityVisibility(
                scope.dataScope(), scope.memberId(), scope.departmentIds(), accessMode));
    }

    public record OpportunityAccess(
            TenantId tenantId, MemberId memberId, OpportunityVisibility visibility) {
    }
}
