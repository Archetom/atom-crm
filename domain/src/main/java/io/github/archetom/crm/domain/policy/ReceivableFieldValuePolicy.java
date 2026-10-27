package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.ReceivableFieldValue;
import io.github.archetom.crm.domain.exception.ReceivableDomainException;

import java.util.List;

/** Receipt and plan custom values deliberately share B05's typed normalization rules. */
public final class ReceivableFieldValuePolicy {
    private ReceivableFieldValuePolicy() {
    }

    public static ReceivableFieldValue normalize(FieldDefinition field, List<String> values) {
        try {
            var normalized = OpportunityFieldValuePolicy.normalize(field, values);
            return normalized == null ? null
                    : new ReceivableFieldValue(normalized.fieldCode(), normalized.values());
        } catch (RuntimeException exception) {
            throw new ReceivableDomainException("receivable custom field value is invalid");
        }
    }

    public static FilterCondition normalizeFilterCondition(
            FieldDefinition field, FilterCondition condition) {
        try {
            return OpportunityFieldValuePolicy.normalizeFilterCondition(field, condition);
        } catch (RuntimeException exception) {
            throw new ReceivableDomainException("receivable filter condition is invalid");
        }
    }
}
