package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class InvoiceProfileId implements ValueObject<InvoiceProfileId> {
    Long value;

    public InvoiceProfileId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("invoice profile ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(InvoiceProfileId other) {
        return other != null && value.equals(other.value);
    }
}
