package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.CustomizationDomainException;

import java.util.regex.Pattern;

/** Stable option value and mutable presentation label. */
public record FieldOption(String value, String label, int order) {

    private static final Pattern VALUE_FORMAT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    public FieldOption {
        if (value == null || !VALUE_FORMAT.matcher(value).matches()) {
            throw new CustomizationDomainException("field option value is invalid");
        }
        if (label == null || label.isBlank() || label.trim().length() > 100) {
            throw new CustomizationDomainException("field option label is invalid");
        }
        if (order < 0 || order > 10000) {
            throw new CustomizationDomainException("field option order is invalid");
        }
        label = label.trim();
    }
}
