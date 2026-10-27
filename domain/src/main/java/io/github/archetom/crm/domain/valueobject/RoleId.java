package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Tenant role identifier. */
@Value
public class RoleId implements ValueObject<RoleId> {

    Long value;

    public RoleId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("role ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(RoleId other) {
        return other != null && value.equals(other.value);
    }
}
