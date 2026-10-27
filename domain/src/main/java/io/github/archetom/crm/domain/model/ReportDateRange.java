package io.github.archetom.crm.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Inclusive, MySQL-safe date range shared by fixed trend and ranking queries. */
public record ReportDateRange(LocalDate from, LocalDate to) {

    private static final LocalDate MINIMUM_DATE = LocalDate.of(1000, 1, 1);
    private static final LocalDate MAXIMUM_DATE = LocalDate.of(9999, 12, 31);

    public ReportDateRange {
        if (from == null || to == null || from.isAfter(to)
                || from.isBefore(MINIMUM_DATE) || to.isAfter(MAXIMUM_DATE)) {
            throw new IllegalArgumentException("report date range is invalid");
        }
    }

    public long inclusiveDays() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    public boolean exceedsYears(int years) {
        if (years < 1) {
            throw new IllegalArgumentException("report year bound is invalid");
        }
        if (from.getYear() > MAXIMUM_DATE.getYear() - years) {
            return false;
        }
        return to.isAfter(from.plusYears(years));
    }

    public List<LocalDate> periodStarts(ReportGranularity granularity, int maximumBuckets) {
        if (granularity == null || maximumBuckets < 1) {
            throw new IllegalArgumentException("report period bound is invalid");
        }
        List<LocalDate> result = new ArrayList<>();
        LocalDate current = granularity.periodStart(from);
        while (!current.isAfter(to)) {
            if (result.size() == maximumBuckets) {
                throw new IllegalArgumentException("report has too many periods");
            }
            result.add(current);
            try {
                current = granularity.next(current);
            } catch (java.time.DateTimeException exception) {
                break;
            }
        }
        return List.copyOf(result);
    }
}
