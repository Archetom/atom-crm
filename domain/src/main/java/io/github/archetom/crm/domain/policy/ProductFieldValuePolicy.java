package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.ProductFieldValue;
import io.github.archetom.crm.domain.exception.ProductDomainException;

import java.util.List;

/** Product adapter over the shared type rules already used by business fields. */
public final class ProductFieldValuePolicy {

    private ProductFieldValuePolicy() {
    }

    public static ProductFieldValue normalize(
            FieldDefinition field, List<String> values) {
        try {
            var normalized = OpportunityFieldValuePolicy.normalize(field, values);
            return normalized == null ? null
                    : new ProductFieldValue(normalized.fieldCode(), normalized.values());
        } catch (RuntimeException exception) {
            throw new ProductDomainException("product custom field value is invalid");
        }
    }

    public static FilterCondition normalizeFilterCondition(
            FieldDefinition field, FilterCondition condition) {
        try {
            return OpportunityFieldValuePolicy.normalizeFilterCondition(field, condition);
        } catch (RuntimeException exception) {
            throw new ProductDomainException("product filter condition is invalid");
        }
    }
}
