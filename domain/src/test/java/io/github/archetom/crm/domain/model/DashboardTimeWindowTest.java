package io.github.archetom.crm.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DashboardTimeWindowTest {

    @Test
    void relativeRangesUseOneUtcDateAndNaturalCalendarBoundaries() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-18T23:30:00Z"),
                ZoneId.of("Asia/Shanghai"));

        assertWindow(clock, DashboardTimeRange.TODAY,
                LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 18),
                ReportGranularity.DAY);
        assertWindow(clock, DashboardTimeRange.CURRENT_WEEK,
                LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19),
                ReportGranularity.DAY);
        assertWindow(clock, DashboardTimeRange.CURRENT_MONTH,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                ReportGranularity.DAY);
        assertWindow(clock, DashboardTimeRange.CURRENT_QUARTER,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30),
                ReportGranularity.WEEK);
        assertWindow(clock, DashboardTimeRange.CURRENT_YEAR,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                ReportGranularity.MONTH);
    }

    @Test
    void leapYearAndUtcDatetimeBoundsRemainExact() {
        DashboardTimeWindow window = DashboardTimeWindow.resolve(
                DashboardTimeRange.CURRENT_YEAR, null, null,
                Clock.fixed(Instant.parse("2024-02-29T12:00:00Z"), ZoneId.of("UTC")));

        assertEquals(366L, window.reportDateRange().inclusiveDays());
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"),
                window.dateTimeBounds().fromInclusiveUtc());
        assertEquals(Instant.parse("2025-01-01T00:00:00Z"),
                window.dateTimeBounds().upperBoundUtc());
        assertEquals(DashboardDateTimeBounds.UpperBoundType.EXCLUSIVE,
                window.dateTimeBounds().upperBoundType());
    }

    @Test
    void comparablePeriodsFollowCalendarBoundariesAndRemainOptionalAtTheMinimumDate() {
        DashboardTimeWindow month = DashboardTimeWindow.resolve(
                DashboardTimeRange.CURRENT_MONTH, null, null,
                Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneId.of("UTC")));
        assertEquals(new ReportDateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)),
                month.previousReportDateRange().orElseThrow());

        DashboardTimeWindow custom = DashboardTimeWindow.resolve(
                DashboardTimeRange.CUSTOM, LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 6),
                Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneId.of("UTC")));
        assertEquals(new ReportDateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3)),
                custom.previousReportDateRange().orElseThrow());

        DashboardTimeWindow minimum = DashboardTimeWindow.resolve(
                DashboardTimeRange.CUSTOM, LocalDate.of(1000, 1, 1), LocalDate.of(1000, 1, 1),
                Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneId.of("UTC")));
        assertTrue(minimum.previousReportDateRange().isEmpty());
    }

    @Test
    void mysqlMaximumDateUsesRepresentableInclusiveUpperBound() {
        DashboardTimeWindow window = DashboardTimeWindow.resolve(
                DashboardTimeRange.CUSTOM,
                LocalDate.of(9999, 12, 31), LocalDate.of(9999, 12, 31),
                Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneId.of("UTC")));

        DashboardDateTimeBounds bounds = window.dateTimeBounds();
        assertEquals(Instant.parse("9999-12-31T00:00:00Z"), bounds.fromInclusiveUtc());
        assertEquals(Instant.parse("9999-12-31T23:59:59.999999Z"),
                bounds.upperBoundUtc());
        assertEquals(DashboardDateTimeBounds.UpperBoundType.INCLUSIVE,
                bounds.upperBoundType());
    }

    @Test
    void customRangesArePreservedAndChooseGranularityAtFixedThresholds() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneId.of("UTC"));

        assertCustomGranularity(clock, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31), ReportGranularity.DAY);
        assertCustomGranularity(clock, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1), ReportGranularity.WEEK);
        assertCustomGranularity(clock, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 30), ReportGranularity.WEEK);
        assertCustomGranularity(clock, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 5, 1), ReportGranularity.MONTH);
        assertCustomGranularity(clock, LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31), ReportGranularity.MONTH);
    }

    @Test
    void invalidOrMismatchedSelectionsFailClosed() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneId.of("UTC"));

        assertThrows(IllegalArgumentException.class,
                () -> DashboardTimeWindow.resolve(DashboardTimeRange.CUSTOM,
                        LocalDate.of(2026, 1, 1), null, clock));
        assertThrows(IllegalArgumentException.class,
                () -> DashboardTimeWindow.resolve(DashboardTimeRange.CURRENT_MONTH,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), clock));
        assertThrows(IllegalArgumentException.class,
                () -> DashboardTimeWindow.resolve(DashboardTimeRange.CUSTOM,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2), clock));
        assertThrows(IllegalArgumentException.class,
                () -> new DashboardTimeWindow(DashboardTimeRange.CURRENT_MONTH,
                        LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 30), ReportGranularity.DAY));
    }

    private static void assertWindow(
            Clock clock,
            DashboardTimeRange selection,
            LocalDate expectedFrom,
            LocalDate expectedTo,
            ReportGranularity expectedGranularity) {
        DashboardTimeWindow window = DashboardTimeWindow.resolve(
                selection, null, null, clock);
        assertEquals(LocalDate.of(2026, 7, 18), window.asOfDate());
        assertEquals(expectedFrom, window.from());
        assertEquals(expectedTo, window.to());
        assertEquals(expectedGranularity, window.granularity());
    }

    private static void assertCustomGranularity(
            Clock clock,
            LocalDate from,
            LocalDate to,
            ReportGranularity expectedGranularity) {
        DashboardTimeWindow window = DashboardTimeWindow.resolve(
                DashboardTimeRange.CUSTOM, from, to, clock);
        assertEquals(from, window.from());
        assertEquals(to, window.to());
        assertEquals(expectedGranularity, window.granularity());
    }
}
