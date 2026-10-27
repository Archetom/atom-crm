package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class LeadIntakeClientId implements ValueObject<LeadIntakeClientId> {
    Long value;

    public LeadIntakeClientId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("lead intake client ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(LeadIntakeClientId other) {
        return other != null && value.equals(other.value);
    }
}
