package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class StageWorkflowRecordId implements ValueObject<StageWorkflowRecordId> {
    Long value;

    public StageWorkflowRecordId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("stage workflow record ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(StageWorkflowRecordId other) {
        return other != null && value.equals(other.value);
    }
}
