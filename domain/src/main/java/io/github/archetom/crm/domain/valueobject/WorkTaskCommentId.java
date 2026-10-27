package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Tenant-scoped identity of a work-task comment. */
@Value
public class WorkTaskCommentId implements ValueObject<WorkTaskCommentId> {

    Long value;

    public WorkTaskCommentId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("work task comment ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(WorkTaskCommentId other) {
        return other != null && value.equals(other.value);
    }
}
