package io.github.archetom.crm.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Inclusive, MySQL-safe UTC calendar range for marketing-form submission queries. */
public record MarketingFormSubmissionDateRange(LocalDate from, LocalDate to) {

    private static final LocalDate MINIMUM_DATE = LocalDate.of(1000, 1, 1);
    private static final LocalDate MAXIMUM_DATE = LocalDate.of(9999, 12, 31);

    public MarketingFormSubmissionDateRange {
        if (from == null || to == null || from.isAfter(to)
                || from.isBefore(MINIMUM_DATE) || to.isAfter(MAXIMUM_DATE)) {
            throw new IllegalArgumentException(
                    "marketing form submission date range is invalid");
        }
    }

    public long inclusiveDays() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    public boolean exceedsYears(int years) {
        if (years < 1) {
            throw new IllegalArgumentException(
                    "marketing form submission year bound is invalid");
        }
        return to.isAfter(from.plusYears(years));
    }

    public List<LocalDate> periodStarts(
            ReportGranularity granularity, int maximumBuckets) {
        if (granularity == null || maximumBuckets < 1) {
            throw new IllegalArgumentException(
                    "marketing form submission period bound is invalid");
        }
        List<LocalDate> result = new ArrayList<>();
        LocalDate current = granularity.periodStart(from);
        while (!current.isAfter(to)) {
            if (result.size() == maximumBuckets) {
                throw new IllegalArgumentException(
                        "marketing form submission has too many periods");
            }
            result.add(current);
            current = granularity.next(current);
        }
        return List.copyOf(result);
    }
}
