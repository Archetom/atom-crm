package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Positive persisted identity of an immutable OA request. */
@Value
public class OaRequestId implements ValueObject<OaRequestId> {

    Long value;

    public OaRequestId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("OA request ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(OaRequestId other) {
        return other != null && value.equals(other.value);
    }
}
