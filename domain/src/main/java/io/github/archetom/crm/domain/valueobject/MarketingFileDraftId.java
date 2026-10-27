package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Opaque identity of an anonymous MarketingForm FILE staging draft. */
@Value
public class MarketingFileDraftId implements ValueObject<MarketingFileDraftId> {
    Long value;

    public MarketingFileDraftId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("marketing file draft ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(MarketingFileDraftId other) {
        return other != null && value.equals(other.value);
    }
}
