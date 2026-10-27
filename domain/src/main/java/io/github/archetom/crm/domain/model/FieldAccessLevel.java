package io.github.archetom.crm.domain.model;

/** Effective field access is independent from the authority that permits a use case. */
public enum FieldAccessLevel {
    HIDDEN,
    READ,
    WRITE;

    public boolean covers(FieldAccessLevel other) {
        return other != null && ordinal() >= other.ordinal();
    }
}
