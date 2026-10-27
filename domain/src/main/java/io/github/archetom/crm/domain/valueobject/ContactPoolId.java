package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Tenant-scoped contact-pool identifier. */
@Value
public class ContactPoolId implements ValueObject<ContactPoolId> {

    Long value;

    public ContactPoolId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("contact pool ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(ContactPoolId other) {
        return other != null && value.equals(other.value);
    }
}
