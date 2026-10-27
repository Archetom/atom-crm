package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ReceivableDomainException;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.util.List;

public record ReceivableFieldValue(FieldCode fieldCode, List<String> values) {
    public ReceivableFieldValue {
        if (fieldCode == null || values == null || values.isEmpty()) {
            throw new ReceivableDomainException("receivable field value is invalid");
        }
        values = List.copyOf(values);
    }
}
