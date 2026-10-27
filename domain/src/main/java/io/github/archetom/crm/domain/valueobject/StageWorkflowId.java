package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class StageWorkflowId implements ValueObject<StageWorkflowId> {
    Long value;

    public StageWorkflowId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("stage workflow ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(StageWorkflowId other) {
        return other != null && value.equals(other.value);
    }
}
