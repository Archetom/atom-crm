package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Tenant-scoped immutable identifier of an Opportunity sales-stage root. */
@Value
public class OpportunityPipelineStageId implements ValueObject<OpportunityPipelineStageId> {

    Long value;

    public OpportunityPipelineStageId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("opportunity pipeline stage ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(OpportunityPipelineStageId other) {
        return other != null && value.equals(other.value);
    }
}
