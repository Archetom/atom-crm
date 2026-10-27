package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.ContactFieldValue;
import io.github.archetom.crm.domain.exception.ContactDomainException;

import java.util.List;

/** Type-aware normalization for values stored by the contact aggregate. */
public final class ContactFieldValuePolicy {

    private ContactFieldValuePolicy() {
    }

    public static ContactFieldValue normalize(FieldDefinition field, List<String> values) {
        try {
            var normalized = OpportunityFieldValuePolicy.normalize(field, values);
            return normalized == null ? null
                    : new ContactFieldValue(normalized.fieldCode(), normalized.values());
        } catch (RuntimeException exception) {
            throw new ContactDomainException("contact custom field value is invalid");
        }
    }

    /** Validates and canonicalizes one-use contact query filter values before SQL translation. */
    public static FilterCondition normalizeFilterCondition(
            FieldDefinition field, FilterCondition condition) {
        try {
            return OpportunityFieldValuePolicy.normalizeFilterCondition(field, condition);
        } catch (RuntimeException exception) {
            throw new ContactDomainException("contact filter condition is invalid");
        }
    }
}
