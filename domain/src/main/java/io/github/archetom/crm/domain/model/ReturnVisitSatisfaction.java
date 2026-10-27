package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ReturnVisitDomainException;

/** Server-owned values for the customer's reported satisfaction with a return visit. */
public enum ReturnVisitSatisfaction {
    VERY_SATISFIED,
    SATISFIED,
    NEUTRAL,
    DISSATISFIED,
    VERY_DISSATISFIED;

    public static ReturnVisitSatisfaction optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new ReturnVisitDomainException("return visit satisfaction is invalid");
        }
    }
}
