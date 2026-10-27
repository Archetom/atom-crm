package io.github.archetom.crm.application.port.out.attachment;

import java.util.Arrays;

/** Opaque binding reference. Its value is intentionally never written into normal logs. */
public final class StorageBindingRef {
    private final byte[] value;

    public StorageBindingRef(byte[] value) {
        if (value == null || value.length < 1 || value.length > 256) {
            throw new IllegalArgumentException("storage binding reference is invalid");
        }
        this.value = Arrays.copyOf(value, value.length);
    }

    public byte[] copyValue() { return Arrays.copyOf(value, value.length); }
    @Override public String toString() { return "StorageBindingRef[redacted]"; }
}
