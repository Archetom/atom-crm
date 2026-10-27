package io.github.archetom.crm.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record CustomReportRow(
        LocalDate periodStart, String dimensionKey, Map<String, BigDecimal> values) {

    public CustomReportRow {
        if (periodStart == null || values == null) {
            throw new IllegalArgumentException("custom report row is invalid");
        }
        values = Map.copyOf(values);
    }
}
