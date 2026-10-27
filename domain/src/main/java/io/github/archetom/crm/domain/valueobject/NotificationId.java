package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Opaque in-app notification identity. */
@Value
public class NotificationId implements ValueObject<NotificationId> {
    Long value;

    public NotificationId(Long value) {
        if (value == null || value <= 0) throw new IllegalArgumentException("notification ID must be positive");
        this.value = value;
    }

    @Override public boolean sameValueAs(NotificationId other) {
        return other != null && value.equals(other.value);
    }
}
