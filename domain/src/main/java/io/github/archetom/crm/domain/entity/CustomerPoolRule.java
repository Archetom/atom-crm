package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.CustomerPoolDomainException;
import io.github.archetom.crm.domain.model.CustomerPoolRuleType;

/** One bounded automatic release rule owned by a customer pool. */
public record CustomerPoolRule(CustomerPoolRuleType type, int inactiveDays) {

    public CustomerPoolRule {
        if (type == null || inactiveDays < 1 || inactiveDays > 3_650) {
            throw new CustomerPoolDomainException("customer pool rule is invalid");
        }
    }
}
