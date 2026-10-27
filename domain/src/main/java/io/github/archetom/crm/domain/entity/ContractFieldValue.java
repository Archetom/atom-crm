package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ContractDomainException;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.util.LinkedHashSet;
import java.util.List;

public record ContractFieldValue(FieldCode fieldCode, List<String> values) {
    public ContractFieldValue {
        if (fieldCode == null || values == null || values.isEmpty() || values.size() > 20) {
            throw new ContractDomainException("contract field value is invalid");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.length() > 500) {
                throw new ContractDomainException("contract field value is invalid");
            }
            normalized.add(value);
        }
        values = List.copyOf(normalized);
    }
}
