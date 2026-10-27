package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.CustomerFieldValue;
import io.github.archetom.crm.domain.exception.CustomerDomainException;

import java.util.List;

/** Type-aware normalization for values stored by the customer aggregate. */
public final class CustomerFieldValuePolicy {

    private CustomerFieldValuePolicy() {
    }

    public static CustomerFieldValue normalize(FieldDefinition field, List<String> values) {
        try {
            var normalized = OpportunityFieldValuePolicy.normalize(field, values);
            return normalized == null ? null
                    : new CustomerFieldValue(normalized.fieldCode(), normalized.values());
        } catch (RuntimeException exception) {
            throw new CustomerDomainException("customer custom field value is invalid");
        }
    }

    /** Validates and canonicalizes one-use customer query filter values before SQL translation. */
    public static FilterCondition normalizeFilterCondition(
            FieldDefinition field, FilterCondition condition) {
        try {
            return OpportunityFieldValuePolicy.normalizeFilterCondition(field, condition);
        } catch (RuntimeException exception) {
            throw new CustomerDomainException("customer filter condition is invalid");
        }
    }
}
