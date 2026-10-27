package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ContactPoolDomainException;
import io.github.archetom.crm.domain.model.ContactPoolRuleType;

/** One bounded automatic release rule owned by a contact pool. */
public record ContactPoolRule(ContactPoolRuleType type, int inactiveDays) {

    public ContactPoolRule {
        if (type == null || inactiveDays < 1 || inactiveDays > 3_650) {
            throw new ContactPoolDomainException("contact pool rule is invalid");
        }
    }
}
