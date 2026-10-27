package io.github.archetom.crm.domain.model;

import java.util.Locale;

public enum ReportMeasureType {
    COUNT,
    SUM;

    public static ReportMeasureType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("report measure type is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("report measure type is invalid");
        }
    }
}
