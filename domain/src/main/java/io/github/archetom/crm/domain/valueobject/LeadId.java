package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Tenant-scoped lead aggregate identifier. */
@Value
public class LeadId implements ValueObject<LeadId> {

    Long value;

    public LeadId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("lead ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(LeadId other) {
        return other != null && value.equals(other.value);
    }
}
