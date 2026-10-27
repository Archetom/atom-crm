package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class ApprovalInstanceId implements ValueObject<ApprovalInstanceId> {
    Long value;

    public ApprovalInstanceId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("approval instance ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(ApprovalInstanceId other) {
        return other != null && value.equals(other.value);
    }
}
