package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Tenant-scoped opportunity aggregate identifier. */
@Value
public class OpportunityId implements ValueObject<OpportunityId> {

    Long value;

    public OpportunityId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("opportunity ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(OpportunityId other) {
        return other != null && value.equals(other.value);
    }
}
