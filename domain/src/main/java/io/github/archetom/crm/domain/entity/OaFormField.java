package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.OaFormDomainException;
import io.github.archetom.crm.domain.valueobject.FieldCode;

/** Ordered OA-form reference; volatile field metadata remains in the B05 catalog. */
public record OaFormField(FieldCode fieldCode, boolean required, int sortOrder) {

    public OaFormField {
        if (fieldCode == null) {
            throw new OaFormDomainException("OA form field code is required");
        }
        if (sortOrder < 0 || sortOrder > 10000) {
            throw new OaFormDomainException("OA form field order is invalid");
        }
    }
}
