package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.util.Map;

/**
 * Whether a caller may see a field's real value.
 *
 * <p>A value may drive a sort, a filter, a copy or an export only when the caller can read it and
 * it is not masked; anything less would let a hidden field be inferred from the result. Several
 * aggregates need that judgement, and each used to carry its own copy of it. Adding a
 * {@link FieldAccessLevel} or a {@link FieldMaskType} then meant editing every copy, and a missed
 * one would keep admitting a field it should now refuse — silently, since the shape still
 * compiles.</p>
 *
 * <p>An absent entry is refused: a field with no resolved access has not been granted.</p>
 */
public final class FieldReadability {

    private FieldReadability() {
    }

    public static boolean readableUnmasked(
            Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        if (accesses == null || code == null) {
            return false;
        }
        EffectiveFieldAccess access = accesses.get(new FieldCode(code));
        return access != null
                && access.accessLevel() != FieldAccessLevel.HIDDEN
                && access.maskType() == FieldMaskType.NONE;
    }
}
