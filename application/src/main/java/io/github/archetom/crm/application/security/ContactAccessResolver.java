package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.ContactVisibility;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves one authority's data scope into a database-ready contact predicate. */
@Component
@RequiredArgsConstructor
public class ContactAccessResolver {

    private final DataScopeVisibilityResolver scopeResolver;

    public ContactAccess access(
            AuthenticatedCaller caller, String authority, ContactAccessMode accessMode) {
        DataScopeVisibilityResolver.ResolvedScope scope =
                scopeResolver.resolve(caller, authority, "Contact");
        return new ContactAccess(scope.tenantId(), scope.memberId(), new ContactVisibility(
                scope.dataScope(), scope.memberId(), scope.departmentIds(), accessMode));
    }

    public record ContactAccess(
            TenantId tenantId, MemberId memberId, ContactVisibility visibility) {
    }
}
