package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class EmbeddedPageId implements ValueObject<EmbeddedPageId> {
    Long value;

    public EmbeddedPageId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("embedded page ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(EmbeddedPageId other) {
        return other != null && value.equals(other.value);
    }
}
