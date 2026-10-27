package io.github.archetom.crm.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implements the transport-side length semantics of {@code SearchKeyword}. */
public final class TrimmedCodePointSizeValidator
        implements ConstraintValidator<TrimmedCodePointSize, CharSequence> {

    private int max;

    @Override
    public void initialize(TrimmedCodePointSize constraint) {
        max = constraint.max();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null || value.toString().isBlank()) {
            return true;
        }
        String normalized = value.toString().trim();
        return normalized.codePointCount(0, normalized.length()) <= max;
    }
}
