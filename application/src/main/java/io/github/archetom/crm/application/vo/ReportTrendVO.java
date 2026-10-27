package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReportTrendVO(
        String metric,
        LocalDate from,
        LocalDate to,
        String granularity,
        List<ReportTrendPointVO> points) {

    public record ReportTrendPointVO(
            LocalDate periodStart, long count, BigDecimal amount) {
    }
}
