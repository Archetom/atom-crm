package io.github.archetom.crm.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One database-aggregated fixed metric value for a calendar period. */
public record ReportTrendPoint(LocalDate periodStart, long count, BigDecimal amount) {

    public ReportTrendPoint {
        if (periodStart == null || count < 0) {
            throw new IllegalArgumentException("report trend point is invalid");
        }
        amount = amount == null ? BigDecimal.ZERO.setScale(2) : amount;
    }
}
