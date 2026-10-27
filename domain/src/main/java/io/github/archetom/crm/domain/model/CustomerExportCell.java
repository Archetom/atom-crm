package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.CustomerDomainException;
import io.github.archetom.crm.domain.valueobject.FieldCode;

/** One scalar customer export cell; null represents an empty CSV cell. */
public record CustomerExportCell(FieldCode fieldCode, String scalarValue) {

    public CustomerExportCell {
        if (fieldCode == null) {
            throw new CustomerDomainException("customer export cell is invalid");
        }
    }
}
