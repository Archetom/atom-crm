package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class ReceivableId implements ValueObject<ReceivableId> {
    Long value;

    public ReceivableId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("receivable ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(ReceivableId other) {
        return other != null && value.equals(other.value);
    }
}
