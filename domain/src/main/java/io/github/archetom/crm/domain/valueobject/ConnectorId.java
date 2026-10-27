package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class ConnectorId implements ValueObject<ConnectorId> {
    Long value;

    public ConnectorId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("connector ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(ConnectorId other) {
        return other != null && value.equals(other.value);
    }
}
