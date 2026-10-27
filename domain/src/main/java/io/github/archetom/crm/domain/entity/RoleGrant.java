package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.AuthorizationDomainException;
import io.github.archetom.crm.domain.model.AuthorityDefinition;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.valueobject.AuthorityCode;

/** Immutable capability and optional data scope granted by a tenant role. */
public record RoleGrant(AuthorityCode authorityCode, DataScope dataScope) {

    public RoleGrant {
        AuthorityDefinition definition = AuthorityCatalog.requireTenantAssignable(authorityCode);
        if (definition.supportsDataScope() && dataScope == null) {
            throw new AuthorizationDomainException("a data-scoped authority requires a scope");
        }
        if (!definition.supportsDataScope() && dataScope != null) {
            throw new AuthorizationDomainException("this authority does not support a data scope");
        }
    }

    public static RoleGrant withoutScope(String authorityCode) {
        return new RoleGrant(new AuthorityCode(authorityCode), null);
    }

    public static RoleGrant withScope(String authorityCode, DataScope dataScope) {
        return new RoleGrant(new AuthorityCode(authorityCode), dataScope);
    }
}
