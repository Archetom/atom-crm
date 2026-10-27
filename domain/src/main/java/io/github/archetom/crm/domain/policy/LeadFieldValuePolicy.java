package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.LeadFieldValue;
import io.github.archetom.crm.domain.exception.LeadDomainException;

import java.util.List;

/** Type-aware normalization for values stored by the lead aggregate. */
public final class LeadFieldValuePolicy {

    private LeadFieldValuePolicy() {
    }

    public static LeadFieldValue normalize(FieldDefinition field, List<String> values) {
        try {
            var normalized = OpportunityFieldValuePolicy.normalize(field, values);
            return normalized == null ? null
                    : new LeadFieldValue(normalized.fieldCode(), normalized.values());
        } catch (RuntimeException exception) {
            throw new LeadDomainException("lead custom field value is invalid");
        }
    }

    /** Validates and canonicalizes one-use lead query filter values before SQL translation. */
    public static FilterCondition normalizeFilterCondition(
            FieldDefinition field, FilterCondition condition) {
        try {
            return OpportunityFieldValuePolicy.normalizeFilterCondition(field, condition);
        } catch (RuntimeException exception) {
            throw new LeadDomainException("lead filter condition is invalid");
        }
    }
}
