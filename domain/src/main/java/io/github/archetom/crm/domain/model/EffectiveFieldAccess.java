package io.github.archetom.crm.domain.model;

/** Merged field policy for one active member. */
public record EffectiveFieldAccess(FieldAccessLevel accessLevel, FieldMaskType maskType) {

    public static EffectiveFieldAccess hidden() {
        return new EffectiveFieldAccess(FieldAccessLevel.HIDDEN, FieldMaskType.FULL);
    }

    public static EffectiveFieldAccess owner() {
        return new EffectiveFieldAccess(FieldAccessLevel.WRITE, FieldMaskType.NONE);
    }
}
