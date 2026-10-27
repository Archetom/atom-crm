package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class SavedViewId implements ValueObject<SavedViewId> {
    Long value;

    public SavedViewId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("saved view ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(SavedViewId other) {
        return other != null && value.equals(other.value);
    }
}
