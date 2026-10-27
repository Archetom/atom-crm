package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Tenant-scoped identity of a marketing activity. */
@Value
public class MarketingActivityId implements ValueObject<MarketingActivityId> {
    Long value;

    public MarketingActivityId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("marketing activity ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(MarketingActivityId other) {
        return other != null && value.equals(other.value);
    }
}
