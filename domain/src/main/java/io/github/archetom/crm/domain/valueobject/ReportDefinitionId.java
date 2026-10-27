package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class ReportDefinitionId implements ValueObject<ReportDefinitionId> {
    Long value;

    public ReportDefinitionId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("report definition ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(ReportDefinitionId other) {
        return other != null && value.equals(other.value);
    }
}
