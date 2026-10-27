package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.model.InvoiceAccessMode;
import io.github.archetom.crm.domain.model.InvoiceVisibility;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves one authority's data scope into a database-ready invoice predicate. */
@Component
@RequiredArgsConstructor
public class InvoiceAccessResolver {

    private final DataScopeVisibilityResolver scopeResolver;

    public InvoiceAccess access(
            AuthenticatedCaller caller, String authority, InvoiceAccessMode mode) {
        DataScopeVisibilityResolver.ResolvedScope scope =
                scopeResolver.resolve(caller, authority, "Invoice");
        return new InvoiceAccess(scope.tenantId(), scope.memberId(), new InvoiceVisibility(
                scope.dataScope(), scope.memberId(), scope.departmentIds(), mode));
    }

    public record InvoiceAccess(
            TenantId tenantId, MemberId memberId, InvoiceVisibility visibility) {
    }
}
