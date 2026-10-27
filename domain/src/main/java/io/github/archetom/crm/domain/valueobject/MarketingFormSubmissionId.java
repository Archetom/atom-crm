package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Tenant-scoped identifier for one successful marketing-form submission fact. */
@Value
public class MarketingFormSubmissionId implements ValueObject<MarketingFormSubmissionId> {

    Long value;

    public MarketingFormSubmissionId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    "marketing form submission ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(MarketingFormSubmissionId other) {
        return other != null && value.equals(other.value);
    }
}
