package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.AuthorityCode;

/** Code-defined capability metadata used by authorization policy and role editors. */
public record AuthorityDefinition(
        AuthorityCode code,
        String displayName,
        boolean tenantAssignable,
        boolean supportsDataScope
) {
}
