package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ActivityDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;

/** A bounded, typed relation shown alongside an activity without granting access to it. */
public record ActivityRelation(BusinessObjectType objectType, Long recordId) {
    public ActivityRelation {
        if (objectType == null || recordId == null || recordId <= 0) {
            throw new ActivityDomainException("activity relation is invalid");
        }
        if (!supported(objectType)) {
            throw new ActivityDomainException("activity relation type is unavailable");
        }
    }

    public static boolean supported(BusinessObjectType value) {
        return value == BusinessObjectType.CUSTOMER || value == BusinessObjectType.CONTACT
                || value == BusinessObjectType.LEAD || value == BusinessObjectType.OPPORTUNITY
                || value == BusinessObjectType.CONTRACT || value == BusinessObjectType.INVOICE
                || value == BusinessObjectType.RECEIVABLE || value == BusinessObjectType.RECEIVABLE_PLAN;
    }
}
