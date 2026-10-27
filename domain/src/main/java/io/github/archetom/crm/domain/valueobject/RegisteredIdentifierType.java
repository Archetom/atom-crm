package io.github.archetom.crm.domain.valueobject;

import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegistryScheme;

import java.util.Objects;

/** One governed identity-scope and validation-scheme combination. */
public record RegisteredIdentifierType(
        IdentityScope identityScope,
        RegistryScheme registryScheme
) implements ValueObject<RegisteredIdentifierType> {

    public RegisteredIdentifierType {
        Objects.requireNonNull(identityScope, "identity scope must not be null");
        Objects.requireNonNull(registryScheme, "registry scheme must not be null");
    }

    @Override
    public boolean sameValueAs(RegisteredIdentifierType other) {
        return equals(other);
    }
}
