package io.github.archetom.crm.application.port.out.attachment;

import java.util.Arrays;

/** Immutable private storage identity of exactly one stored content generation. */
public final class StoredObjectIdentity {
    private final byte[] value;
    public StoredObjectIdentity(byte[] value) {
        if (value == null || value.length < 1 || value.length > 512) {
            throw new IllegalArgumentException("stored object identity is invalid");
        }
        this.value = Arrays.copyOf(value, value.length);
    }
    public byte[] copyValue() { return Arrays.copyOf(value, value.length); }
    @Override public String toString() { return "StoredObjectIdentity[redacted]"; }
}
