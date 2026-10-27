package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Positive persisted identity of a membership application. */
@Value
public class MembershipRequestId implements ValueObject<MembershipRequestId> {

    Long value;

    public MembershipRequestId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("membership request ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(MembershipRequestId other) {
        return other != null && value.equals(other.value);
    }
}
