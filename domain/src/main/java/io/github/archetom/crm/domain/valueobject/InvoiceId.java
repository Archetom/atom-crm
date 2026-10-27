package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class InvoiceId implements ValueObject<InvoiceId> {
    Long value;

    public InvoiceId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("invoice ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(InvoiceId other) {
        return other != null && value.equals(other.value);
    }
}
