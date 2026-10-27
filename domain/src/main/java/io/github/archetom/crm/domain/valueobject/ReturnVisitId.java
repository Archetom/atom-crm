package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class ReturnVisitId implements ValueObject<ReturnVisitId> {
    Long value;

    public ReturnVisitId(Long value) {
        if (value == null || value <= 0) throw new IllegalArgumentException("return visit ID must be positive");
        this.value = value;
    }

    @Override
    public boolean sameValueAs(ReturnVisitId other) {
        return other != null && value.equals(other.value);
    }
}
