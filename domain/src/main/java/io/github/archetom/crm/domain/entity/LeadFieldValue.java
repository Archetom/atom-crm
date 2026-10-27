package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.LeadDomainException;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.util.LinkedHashSet;
import java.util.List;

/** Bounded value representation for one active lead field definition. */
public record LeadFieldValue(FieldCode fieldCode, List<String> values) {

    public static final int MAX_VALUES = 20;
    public static final int MAX_VALUE_LENGTH = 500;

    public LeadFieldValue {
        if (fieldCode == null || values == null || values.isEmpty() || values.size() > MAX_VALUES) {
            throw new LeadDomainException("lead field value is invalid");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.length() > MAX_VALUE_LENGTH) {
                throw new LeadDomainException("lead field value is invalid");
            }
            normalized.add(value);
        }
        values = List.copyOf(normalized);
    }
}
