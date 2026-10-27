package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Positive persisted identity of an organization membership. */
@Value
public class MembershipId implements ValueObject<MembershipId> {

    Long value;

    public MembershipId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("membership ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(MembershipId other) {
        return other != null && value.equals(other.value);
    }
}
