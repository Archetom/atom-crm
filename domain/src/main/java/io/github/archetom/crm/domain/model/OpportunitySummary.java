package io.github.archetom.crm.domain.model;

import java.math.BigDecimal;

/** Aggregate values computed over the same visible and field-authorized query predicate. */
public record OpportunitySummary(long count, BigDecimal estimatedAmount) {

    public OpportunitySummary {
        if (count < 0) {
            throw new IllegalArgumentException("opportunity summary count must not be negative");
        }
        estimatedAmount = estimatedAmount == null ? BigDecimal.ZERO : estimatedAmount;
    }
}
