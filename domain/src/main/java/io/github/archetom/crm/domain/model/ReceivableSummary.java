package io.github.archetom.crm.domain.model;

import java.math.BigDecimal;

/** Aggregate receipt values computed only over records visible to the caller. */
public record ReceivableSummary(long count, BigDecimal totalAmount) {

    public ReceivableSummary {
        if (count < 0) {
            throw new IllegalArgumentException("receivable summary count must not be negative");
        }
        totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
    }
}
