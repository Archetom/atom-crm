package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class ContractId implements ValueObject<ContractId> {
    Long value;

    public ContractId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("contract ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(ContractId other) {
        return other != null && value.equals(other.value);
    }
}
