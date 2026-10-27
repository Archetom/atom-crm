package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.LeadDomainException;
import io.github.archetom.crm.domain.valueobject.FieldCode;

/** One scalar Lead export cell; null represents an empty CSV cell. */
public record LeadExportCell(FieldCode fieldCode, String scalarValue) {

    public LeadExportCell {
        if (fieldCode == null) {
            throw new LeadDomainException("lead export cell is invalid");
        }
    }
}
