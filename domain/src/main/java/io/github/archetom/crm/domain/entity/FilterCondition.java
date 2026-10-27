package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.CustomizationDomainException;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.util.List;

/** A bounded, typed condition that downstream repositories translate into parameterized predicates. */
public record FilterCondition(FieldCode fieldCode, FilterOperator operator, List<String> values) {

    public static final int MAX_VALUES = 20;

    public FilterCondition {
        if (fieldCode == null || operator == null) {
            throw new CustomizationDomainException("filter field and operator are required");
        }
        values = values == null ? List.of() : List.copyOf(values);
        if (values.size() > MAX_VALUES || values.stream()
                .anyMatch(value -> value == null || value.length() > 500)) {
            throw new CustomizationDomainException("filter values are invalid");
        }
        if (!operator.acceptsValueCount(values.size())) {
            throw new CustomizationDomainException("filter operator has an invalid value count");
        }
    }
}
