package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.CustomerDomainException;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.util.LinkedHashSet;
import java.util.List;

/** Bounded value representation for one active customer field definition. */
public record CustomerFieldValue(FieldCode fieldCode, List<String> values) {

    public static final int MAX_VALUES = 20;
    public static final int MAX_VALUE_LENGTH = 500;

    public CustomerFieldValue {
        if (fieldCode == null || values == null || values.isEmpty() || values.size() > MAX_VALUES) {
            throw new CustomerDomainException("customer field value is invalid");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.length() > MAX_VALUE_LENGTH) {
                throw new CustomerDomainException("customer field value is invalid");
            }
            normalized.add(value);
        }
        values = List.copyOf(normalized);
    }
}
