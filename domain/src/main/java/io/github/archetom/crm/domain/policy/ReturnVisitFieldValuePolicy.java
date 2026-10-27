package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.ReturnVisitFieldValue;
import io.github.archetom.crm.domain.exception.ReturnVisitDomainException;

import java.util.List;

public final class ReturnVisitFieldValuePolicy {
    private ReturnVisitFieldValuePolicy() {
    }

    public static ReturnVisitFieldValue normalize(FieldDefinition field, List<String> values) {
        try {
            var normalized = OpportunityFieldValuePolicy.normalize(field, values);
            return normalized == null ? null : new ReturnVisitFieldValue(
                    normalized.fieldCode(), normalized.values());
        } catch (RuntimeException exception) {
            throw new ReturnVisitDomainException("return visit custom field value is invalid");
        }
    }

    public static FilterCondition normalizeFilterCondition(
            FieldDefinition field, FilterCondition condition) {
        try {
            return OpportunityFieldValuePolicy.normalizeFilterCondition(field, condition);
        } catch (RuntimeException exception) {
            throw new ReturnVisitDomainException("return visit filter condition is invalid");
        }
    }
}
