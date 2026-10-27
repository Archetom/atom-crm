package io.github.archetom.crm.domain.model;

import java.math.BigDecimal;

/** One permission-filtered dashboard source total for a bounded calendar period. */
public record DashboardMetricSummary(long count, BigDecimal amount) {

    public DashboardMetricSummary {
        if (count < 0) {
            throw new IllegalArgumentException("dashboard metric count must not be negative");
        }
        amount = amount == null ? BigDecimal.ZERO.setScale(2) : amount;
    }
}
