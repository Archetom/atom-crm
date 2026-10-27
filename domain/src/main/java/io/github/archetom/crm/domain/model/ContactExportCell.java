package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ContactDomainException;
import io.github.archetom.crm.domain.valueobject.FieldCode;

/** One scalar Contact export cell; null represents an empty CSV cell. */
public record ContactExportCell(FieldCode fieldCode, String scalarValue) {

    public ContactExportCell {
        if (fieldCode == null) {
            throw new ContactDomainException("contact export cell is invalid");
        }
    }
}
