package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.MarketingFormDomainException;
import io.github.archetom.crm.domain.valueobject.FieldCode;

/** One public input configured on a marketing form. Field types remain in the LEAD field catalog. */
public record MarketingFormField(
        FieldCode fieldCode,
        String label,
        String placeholder,
        String helpText,
        boolean required,
        int sortOrder
) {
    public MarketingFormField {
        if (fieldCode == null) {
            throw new MarketingFormDomainException("marketing form field code is required");
        }
        label = requiredText(label, 100, "marketing form field label");
        placeholder = optionalText(placeholder, 200, "marketing form field placeholder");
        helpText = optionalText(helpText, 500, "marketing form field help text");
        if (sortOrder < 0 || sortOrder > 10000) {
            throw new MarketingFormDomainException("marketing form field order is invalid");
        }
    }

    private static String requiredText(String value, int max, String description) {
        if (value == null || value.isBlank()) {
            throw new MarketingFormDomainException(description + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new MarketingFormDomainException(description + " is too long");
        }
        return normalized;
    }

    private static String optionalText(String value, int max, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new MarketingFormDomainException(description + " is too long");
        }
        return normalized;
    }
}
