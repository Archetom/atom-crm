package io.github.archetom.crm.domain.policy;

import java.text.Normalizer;
import java.util.Locale;

/** Canonical key form shared by identifier uniqueness and normalized identifier search. */
public final class IdentifierKeyNormalizer {

    private IdentifierKeyNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("identifier key value is required");
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
