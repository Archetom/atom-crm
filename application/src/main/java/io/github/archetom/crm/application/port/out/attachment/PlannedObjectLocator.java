package io.github.archetom.crm.application.port.out.attachment;

import java.util.Arrays;

/** Durable, exact, private locator allocated before storage I/O. */
public final class PlannedObjectLocator {
    private final byte[] value;
    public PlannedObjectLocator(byte[] value) {
        if (value == null || value.length < 1 || value.length > 512) {
            throw new IllegalArgumentException("planned object locator is invalid");
        }
        this.value = Arrays.copyOf(value, value.length);
    }
    public byte[] copyValue() { return Arrays.copyOf(value, value.length); }
    @Override public String toString() { return "PlannedObjectLocator[redacted]"; }
}
