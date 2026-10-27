package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.model.ReceivableVisibility;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves one authority's data scope into a database-ready receivable predicate. */
@Component
@RequiredArgsConstructor
public class ReceivableAccessResolver {

    private final DataScopeVisibilityResolver scopeResolver;

    public ReceivableAccess access(
            AuthenticatedCaller caller, String authority, ReceivableAccessMode mode) {
        DataScopeVisibilityResolver.ResolvedScope scope =
                scopeResolver.resolve(caller, authority, "Receivable");
        return new ReceivableAccess(scope.tenantId(), scope.memberId(), new ReceivableVisibility(
                scope.dataScope(), scope.memberId(), scope.departmentIds(), mode));
    }

    public record ReceivableAccess(
            TenantId tenantId, MemberId memberId, ReceivableVisibility visibility) {
    }
}
