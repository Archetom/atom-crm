package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ActivityDomainException;
import io.github.archetom.crm.domain.model.ActivityOptionType;

import java.text.Normalizer;
import java.util.Locale;

public record ActivityOption(ActivityOptionType type, String value, String label, int displayOrder) {
    public ActivityOption {
        if (type == null || displayOrder < 0 || displayOrder > 10_000) {
            throw new ActivityDomainException("activity option is invalid");
        }
        value = normalizeValue(value);
        label = normalizeLabel(label);
    }

    public static String normalizeValue(String value) {
        if (value == null || value.isBlank()) {
            throw new ActivityDomainException("activity option value is required");
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9][A-Z0-9_-]{0,63}")) {
            throw new ActivityDomainException("activity option value is invalid");
        }
        return normalized;
    }

    private static String normalizeLabel(String value) {
        if (value == null || value.isBlank()) {
            throw new ActivityDomainException("activity option label is required");
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC);
        if (normalized.codePointCount(0, normalized.length()) > 200) {
            throw new ActivityDomainException("activity option label is too long");
        }
        return normalized;
    }
}
