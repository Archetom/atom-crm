package io.github.archetom.crm.domain.valueobject;

import java.io.Serial;
import java.util.Objects;

/**
 * One-way password hash stored by the user aggregate.
 *
 * <p>The raw hash is available only through the deliberately named persistence
 * accessor. It is never exposed through a JavaBean getter or {@link #toString()}.</p>
 */
public final class PasswordHash implements ValueObject<PasswordHash> {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int MAX_STORAGE_LENGTH = 255;

    private final String value;

    private PasswordHash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Password hash must not be blank");
        }
        if (value.length() > MAX_STORAGE_LENGTH) {
            throw new IllegalArgumentException("Password hash exceeds the storage limit");
        }
        this.value = value;
    }

    /**
     * Reconstitutes a hash produced by a trusted password-hashing adapter.
     */
    public static PasswordHash fromTrustedHash(String value) {
        return new PasswordHash(value);
    }

    /**
     * Returns the encoded hash for persistence or password verification only.
     */
    public String valueForPersistence() {
        return value;
    }

    @Override
    public boolean sameValueAs(PasswordHash other) {
        return other != null && value.equals(other.value);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PasswordHash passwordHash
                && value.equals(passwordHash.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "[REDACTED]";
    }
}
