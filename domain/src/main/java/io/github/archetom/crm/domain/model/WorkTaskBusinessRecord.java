package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.WorkTaskDomainException;

import java.util.EnumSet;
import java.util.Set;

/** One optional CRM record associated with a work task. */
public record WorkTaskBusinessRecord(BusinessObjectType objectType, Long recordId) {

    private static final Set<BusinessObjectType> SUPPORTED_TYPES = Set.copyOf(EnumSet.of(
            BusinessObjectType.CUSTOMER,
            BusinessObjectType.CONTACT,
            BusinessObjectType.LEAD,
            BusinessObjectType.OPPORTUNITY,
            BusinessObjectType.CONTRACT,
            BusinessObjectType.INVOICE,
            BusinessObjectType.RECEIVABLE,
            BusinessObjectType.RECEIVABLE_PLAN));

    public WorkTaskBusinessRecord {
        if (!SUPPORTED_TYPES.contains(objectType) || recordId == null || recordId <= 0) {
            throw new WorkTaskDomainException("work task business record is invalid");
        }
    }

    public static boolean supports(BusinessObjectType objectType) {
        return SUPPORTED_TYPES.contains(objectType);
    }
}
