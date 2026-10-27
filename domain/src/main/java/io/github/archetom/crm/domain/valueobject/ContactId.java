package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Tenant-scoped contact aggregate identifier. */
@Value
public class ContactId implements ValueObject<ContactId> {

    Long value;

    public ContactId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("contact ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(ContactId other) {
        return other != null && value.equals(other.value);
    }
}
