package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class OaFormId implements ValueObject<OaFormId> {
    Long value;

    public OaFormId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("OA form ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(OaFormId other) {
        return other != null && value.equals(other.value);
    }
}
