package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class ApprovalDefinitionId implements ValueObject<ApprovalDefinitionId> {
    Long value;

    public ApprovalDefinitionId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("approval definition ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(ApprovalDefinitionId other) {
        return other != null && value.equals(other.value);
    }
}
