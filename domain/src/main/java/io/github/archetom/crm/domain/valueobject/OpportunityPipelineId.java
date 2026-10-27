package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Tenant-scoped immutable identifier of an Opportunity sales pipeline root. */
@Value
public class OpportunityPipelineId implements ValueObject<OpportunityPipelineId> {

    Long value;

    public OpportunityPipelineId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("opportunity pipeline ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(OpportunityPipelineId other) {
        return other != null && value.equals(other.value);
    }
}
