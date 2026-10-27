package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class FieldDefinitionId implements ValueObject<FieldDefinitionId> {
    Long value;

    public FieldDefinitionId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("field definition ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(FieldDefinitionId other) {
        return other != null && value.equals(other.value);
    }
}
