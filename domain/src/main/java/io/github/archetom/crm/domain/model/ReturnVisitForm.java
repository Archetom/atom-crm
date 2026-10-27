package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ReturnVisitDomainException;

/** Server-owned values for the way a customer return visit was conducted. */
public enum ReturnVisitForm {
    IN_PERSON,
    PHONE,
    SMS,
    EMAIL,
    WECHAT;

    public static ReturnVisitForm optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new ReturnVisitDomainException("return visit form is invalid");
        }
    }
}
