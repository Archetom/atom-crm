package io.github.archetom.crm.domain.valueobject;

import java.util.Optional;

/**
 * A bounded literal search term. Blank input deliberately means that no keyword filter is applied.
 */
public record SearchKeyword(String value) implements ValueObject<SearchKeyword> {

    public static final int MAX_CODE_POINTS = 200;

    public SearchKeyword {
        value = requireNormalized(value);
    }

    /**
     * Normalizes an optional transport value. A {@code null} or blank value represents no filter.
     */
    public static Optional<SearchKeyword> optional(String value) {
        String normalized = normalize(value);
        return normalized == null ? Optional.empty() : Optional.of(new SearchKeyword(normalized));
    }

    /**
     * Returns the canonical optional form used by query boundaries.
     */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.codePointCount(0, normalized.length()) > MAX_CODE_POINTS) {
            throw new IllegalArgumentException("search keyword must not exceed "
                    + MAX_CODE_POINTS + " characters");
        }
        return normalized;
    }

    @Override
    public boolean sameValueAs(SearchKeyword other) {
        return other != null && value.equals(other.value);
    }

    private static String requireNormalized(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException("search keyword must not be blank");
        }
        return normalized;
    }
}
