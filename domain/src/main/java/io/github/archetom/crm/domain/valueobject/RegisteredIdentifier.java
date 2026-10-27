package io.github.archetom.crm.domain.valueobject;

import io.github.archetom.crm.domain.model.IdentifierStanding;
import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegistryScheme;

import java.util.Objects;

/** Scheme-validated registry identity plus its orthogonal observed standing. */
public final class RegisteredIdentifier implements ValueObject<RegisteredIdentifier> {

    private final RegisteredIdentifierKey key;
    private final IdentifierStanding identifierStanding;

    public RegisteredIdentifier(
            IdentityScope identityScope,
            RegistryScheme registryScheme,
            String value,
            IdentifierStanding identifierStanding) {
        this(new RegisteredIdentifierKey(identityScope, registryScheme, value), identifierStanding);
    }

    public RegisteredIdentifier(
            RegisteredIdentifierKey key, IdentifierStanding identifierStanding) {
        this.key = Objects.requireNonNull(key, "identifier key must not be null");
        this.identifierStanding = Objects.requireNonNull(identifierStanding,
                "identifier standing must not be null");
    }

    public RegisteredIdentifierKey key() {
        return key;
    }

    public IdentityScope getIdentityScope() {
        return key.getIdentityScope();
    }

    public RegistryScheme getRegistryScheme() {
        return key.getRegistryScheme();
    }

    public String getCanonicalValue() {
        return key.getCanonicalValue();
    }

    public IdentifierStanding getIdentifierStanding() {
        return identifierStanding;
    }

    @Override
    public boolean sameValueAs(RegisteredIdentifier other) {
        return equals(other);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RegisteredIdentifier that)) return false;
        return key.equals(that.key) && identifierStanding == that.identifierStanding;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, identifierStanding);
    }

    @Override
    public String toString() {
        return "RegisteredIdentifier[redacted]";
    }
}
