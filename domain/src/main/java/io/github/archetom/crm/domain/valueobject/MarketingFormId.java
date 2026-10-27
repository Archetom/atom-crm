package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class MarketingFormId implements ValueObject<MarketingFormId> {
    Long value;

    public MarketingFormId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("marketing form ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(MarketingFormId other) {
        return other != null && value.equals(other.value);
    }
}
