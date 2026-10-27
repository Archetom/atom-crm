package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class StageWorkflowCommentId implements ValueObject<StageWorkflowCommentId> {
    Long value;

    public StageWorkflowCommentId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("stage workflow comment ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(StageWorkflowCommentId other) {
        return other != null && value.equals(other.value);
    }
}
