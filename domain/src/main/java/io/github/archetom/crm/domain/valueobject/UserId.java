package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Positive persisted identity of a User aggregate. */
@Value
public class UserId implements ValueObject<UserId> {

    Long value;

    public UserId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }
        this.value = value;
    }

    public static UserId of(Long value) {
        return new UserId(value);
    }

    @Override
    public boolean sameValueAs(UserId other) {
        return other != null && this.value.equals(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
