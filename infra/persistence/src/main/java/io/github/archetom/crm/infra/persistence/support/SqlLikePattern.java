package io.github.archetom.crm.infra.persistence.support;

import io.github.archetom.crm.domain.policy.IdentifierKeyNormalizer;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;

/** Escapes a literal for a SQL LIKE expression that declares {@code ESCAPE '!'}. */
public final class SqlLikePattern {

    private static final char ESCAPE_CHARACTER = '!';

    private SqlLikePattern() {
    }

    public static String literal(String value) {
        if (value == null) {
            return null;
        }
        String escape = Character.toString(ESCAPE_CHARACTER);
        return value.replace(escape, escape + escape)
                .replace("%", escape + "%")
                .replace("_", escape + "_");
    }

    /**
     * Canonicalizes an identifier key before escaping it for a LIKE-prefix comparison.
     * Query adapters append the sole trailing wildcard after this value.
     */
    public static String normalizedIdentifierPrefixLiteral(SearchKeyword keyword) {
        if (keyword == null) {
            return null;
        }
        return literal(IdentifierKeyNormalizer.normalize(keyword.value()));
    }
}
