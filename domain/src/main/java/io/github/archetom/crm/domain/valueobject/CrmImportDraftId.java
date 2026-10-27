package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Opaque identity of an authenticated CRM import draft. */
@Value
public class CrmImportDraftId implements ValueObject<CrmImportDraftId> {
    Long value;

    public CrmImportDraftId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("CRM import draft ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(CrmImportDraftId other) {
        return other != null && value.equals(other.value);
    }
}
