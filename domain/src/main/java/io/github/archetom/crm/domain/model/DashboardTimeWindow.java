package io.github.archetom.crm.domain.model;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/**
 * One dashboard request's immutable UTC date window and automatically selected
 * report granularity.
 */
public record DashboardTimeWindow(
        DashboardTimeRange timeRange,
        LocalDate asOfDate,
        LocalDate from,
        LocalDate to,
        ReportGranularity granularity) {

    public static final int MAXIMUM_DAYS = 366;
    private static final LocalDate MAXIMUM_MYSQL_DATE = LocalDate.of(9999, 12, 31);
    private static final int MAXIMUM_DAY_GRANULARITY_DAYS = 31;
    private static final int MAXIMUM_WEEK_GRANULARITY_DAYS = 120;

    public DashboardTimeWindow {
        if (timeRange == null || asOfDate == null || from == null || to == null
                || granularity == null) {
            throw new IllegalArgumentException("dashboard time window must be complete");
        }
        ReportDateRange range = new ReportDateRange(from, to);
        if (range.inclusiveDays() > MAXIMUM_DAYS) {
            throw new IllegalArgumentException("dashboard time window exceeds the supported limit");
        }
        DateBounds expected = timeRange == DashboardTimeRange.CUSTOM
                ? new DateBounds(from, to)
                : relativeBounds(timeRange, asOfDate);
        if (!expected.from().equals(from) || !expected.to().equals(to)) {
            throw new IllegalArgumentException("dashboard time window does not match its selection");
        }
        if (granularity != granularity(range.inclusiveDays())) {
            throw new IllegalArgumentException("dashboard time window granularity is invalid");
        }
    }

    /** Resolve the current date from the clock's instant in UTC, regardless of its configured zone. */
    public static DashboardTimeWindow resolve(
            DashboardTimeRange timeRange,
            LocalDate customStartDate,
            LocalDate customEndDate,
            Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("dashboard clock must not be null");
        }
        LocalDate asOfDate = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        DateBounds bounds = bounds(timeRange, asOfDate, customStartDate, customEndDate);
        ReportDateRange range = new ReportDateRange(bounds.from(), bounds.to());
        if (range.inclusiveDays() > MAXIMUM_DAYS) {
            throw new IllegalArgumentException("dashboard time window exceeds the supported limit");
        }
        return new DashboardTimeWindow(timeRange, asOfDate, bounds.from(), bounds.to(),
                granularity(range.inclusiveDays()));
    }

    public ReportDateRange reportDateRange() {
        return new ReportDateRange(from, to);
    }

    /** Returns the immediately preceding comparable calendar window when it is representable. */
    public Optional<ReportDateRange> previousReportDateRange() {
        try {
            return Optional.of(switch (timeRange) {
                case TODAY, CURRENT_WEEK -> new ReportDateRange(
                        from.minusDays(reportDateRange().inclusiveDays()),
                        to.minusDays(reportDateRange().inclusiveDays()));
                case CURRENT_MONTH -> new ReportDateRange(
                        from.minusMonths(1), from.minusDays(1));
                case CURRENT_QUARTER -> new ReportDateRange(
                        from.minusMonths(3), from.minusDays(1));
                case CURRENT_YEAR -> new ReportDateRange(
                        from.minusYears(1), from.minusDays(1));
                case CUSTOM -> new ReportDateRange(
                        from.minusDays(reportDateRange().inclusiveDays()), from.minusDays(1));
            });
        } catch (java.time.DateTimeException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /**
     * Produces a half-open UTC range unless the selected end is MySQL's maximum
     * date, where the maximum representable microsecond becomes an inclusive
     * upper bound instead of creating an unsupported year-10000 value.
     */
    public DashboardDateTimeBounds dateTimeBounds() {
        Instant fromInclusive = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        if (to.equals(MAXIMUM_MYSQL_DATE)) {
            Instant maximumInclusive = to.atTime(23, 59, 59, 999_999_000)
                    .toInstant(ZoneOffset.UTC);
            return new DashboardDateTimeBounds(fromInclusive, maximumInclusive,
                    DashboardDateTimeBounds.UpperBoundType.INCLUSIVE);
        }
        Instant toExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new DashboardDateTimeBounds(fromInclusive, toExclusive,
                DashboardDateTimeBounds.UpperBoundType.EXCLUSIVE);
    }

    private static DateBounds bounds(
            DashboardTimeRange timeRange,
            LocalDate asOfDate,
            LocalDate customStartDate,
            LocalDate customEndDate) {
        if (timeRange == null || asOfDate == null) {
            throw new IllegalArgumentException("dashboard time range must be complete");
        }
        if (timeRange == DashboardTimeRange.CUSTOM) {
            if (customStartDate == null || customEndDate == null) {
                throw new IllegalArgumentException("custom dashboard time range requires both dates");
            }
            return new DateBounds(customStartDate, customEndDate);
        }
        if (customStartDate != null || customEndDate != null) {
            throw new IllegalArgumentException(
                    "custom dates are only valid for the CUSTOM dashboard time range");
        }
        return relativeBounds(timeRange, asOfDate);
    }

    private static DateBounds relativeBounds(
            DashboardTimeRange timeRange, LocalDate asOfDate) {
        return switch (timeRange) {
            case TODAY -> new DateBounds(asOfDate, asOfDate);
            case CURRENT_WEEK -> {
                LocalDate start = asOfDate.with(
                        TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new DateBounds(start, start.plusDays(6));
            }
            case CURRENT_MONTH -> {
                LocalDate start = asOfDate.withDayOfMonth(1);
                yield new DateBounds(start, start.plusMonths(1).minusDays(1));
            }
            case CURRENT_QUARTER -> {
                int firstMonth = ((asOfDate.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate start = LocalDate.of(asOfDate.getYear(), firstMonth, 1);
                yield new DateBounds(start, start.plusMonths(3).minusDays(1));
            }
            case CURRENT_YEAR -> {
                LocalDate start = asOfDate.withDayOfYear(1);
                yield new DateBounds(start, start.plusYears(1).minusDays(1));
            }
            case CUSTOM -> throw new IllegalArgumentException(
                    "CUSTOM requires explicit dashboard dates");
        };
    }

    private static ReportGranularity granularity(long inclusiveDays) {
        if (inclusiveDays <= MAXIMUM_DAY_GRANULARITY_DAYS) {
            return ReportGranularity.DAY;
        }
        if (inclusiveDays <= MAXIMUM_WEEK_GRANULARITY_DAYS) {
            return ReportGranularity.WEEK;
        }
        return ReportGranularity.MONTH;
    }

    private record DateBounds(LocalDate from, LocalDate to) {
    }
}
