package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.InvoiceFieldValue;
import io.github.archetom.crm.domain.exception.InvoiceDomainException;

import java.util.List;

public final class InvoiceFieldValuePolicy {
    private InvoiceFieldValuePolicy() {
    }

    public static InvoiceFieldValue normalize(
            FieldDefinition field, List<String> values) {
        try {
            var normalized = OpportunityFieldValuePolicy.normalize(field, values);
            return normalized == null ? null
                    : new InvoiceFieldValue(normalized.fieldCode(), normalized.values());
        } catch (RuntimeException exception) {
            throw new InvoiceDomainException("invoice custom field value is invalid");
        }
    }

    public static FilterCondition normalizeFilterCondition(
            FieldDefinition field, FilterCondition condition) {
        try {
            return OpportunityFieldValuePolicy.normalizeFilterCondition(field, condition);
        } catch (RuntimeException exception) {
            throw new InvoiceDomainException("invoice filter condition is invalid");
        }
    }
}
