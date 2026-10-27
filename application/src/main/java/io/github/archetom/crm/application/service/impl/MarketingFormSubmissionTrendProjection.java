package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.domain.model.MarketingFormSubmissionTrendPoint;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates and zero-fills one database-aggregated submission trend. */
final class MarketingFormSubmissionTrendProjection {

    private MarketingFormSubmissionTrendProjection() {
    }

    static List<MarketingFormSubmissionTrendPoint> complete(
            List<LocalDate> periods,
            List<MarketingFormSubmissionTrendPoint> values) {
        if (periods == null || periods.isEmpty() || values == null) {
            throw new IllegalStateException("marketing form submission trend is incomplete");
        }
        Set<LocalDate> expected = new LinkedHashSet<>(periods);
        if (expected.size() != periods.size() || expected.contains(null)) {
            throw new IllegalStateException("marketing form submission periods are invalid");
        }
        Map<LocalDate, MarketingFormSubmissionTrendPoint> byPeriod =
                new LinkedHashMap<>();
        for (MarketingFormSubmissionTrendPoint value : values) {
            if (value == null || !expected.contains(value.periodStart())
                    || byPeriod.put(value.periodStart(), value) != null) {
                throw new IllegalStateException(
                        "marketing form submission trend projection is invalid");
            }
        }
        return periods.stream().map(period -> {
            MarketingFormSubmissionTrendPoint value = byPeriod.get(period);
            return value == null
                    ? new MarketingFormSubmissionTrendPoint(period, 0L) : value;
        }).toList();
    }
}
