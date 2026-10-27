package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class ReceivablePlanId implements ValueObject<ReceivablePlanId> {
    Long value;

    public ReceivablePlanId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("receivable plan ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(ReceivablePlanId other) {
        return other != null && value.equals(other.value);
    }
}
