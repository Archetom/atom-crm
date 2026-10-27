package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Verified actor identifier used by the organization model. */
@Value
public class MemberId implements ValueObject<MemberId> {

    Long value;

    public MemberId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("member ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(MemberId other) {
        return other != null && value.equals(other.value);
    }
}
