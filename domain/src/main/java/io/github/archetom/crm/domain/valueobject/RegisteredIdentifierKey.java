package io.github.archetom.crm.domain.valueobject;

import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegistryScheme;
import io.github.archetom.crm.domain.policy.RegisteredIdentifierCatalog;

import java.util.Objects;

/** Exact registry identity key; string rendering is deliberately redacted. */
public final class RegisteredIdentifierKey implements ValueObject<RegisteredIdentifierKey> {

    private final IdentityScope identityScope;
    private final RegistryScheme registryScheme;
    private final String canonicalValue;

    public RegisteredIdentifierKey(
            IdentityScope identityScope, RegistryScheme registryScheme, String value) {
        this.identityScope = Objects.requireNonNull(identityScope,
                "identity scope must not be null");
        this.registryScheme = Objects.requireNonNull(registryScheme,
                "registry scheme must not be null");
        this.canonicalValue = RegisteredIdentifierCatalog.canonicalize(
                identityScope, registryScheme, value);
    }

    public IdentityScope getIdentityScope() {
        return identityScope;
    }

    public RegistryScheme getRegistryScheme() {
        return registryScheme;
    }

    public String getCanonicalValue() {
        return canonicalValue;
    }

    public RegisteredIdentifierType type() {
        return new RegisteredIdentifierType(identityScope, registryScheme);
    }

    @Override
    public boolean sameValueAs(RegisteredIdentifierKey other) {
        return equals(other);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RegisteredIdentifierKey that)) return false;
        return identityScope == that.identityScope
                && registryScheme == that.registryScheme
                && canonicalValue.equals(that.canonicalValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identityScope, registryScheme, canonicalValue);
    }

    @Override
    public String toString() {
        return "RegisteredIdentifierKey[redacted]";
    }
}
