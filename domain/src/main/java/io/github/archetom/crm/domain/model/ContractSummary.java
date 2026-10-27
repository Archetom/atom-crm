package io.github.archetom.crm.domain.model;

import java.math.BigDecimal;

/** Aggregate contract values computed only over records visible to the caller. */
public record ContractSummary(long count, BigDecimal totalAmount) {

    public ContractSummary {
        if (count < 0) {
            throw new IllegalArgumentException("contract summary count must not be negative");
        }
        totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
    }
}
