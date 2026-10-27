package io.github.archetom.crm.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportDateRangeTest {

    @Test
    void periodStartsUseDayIsoWeekAndNaturalMonthBoundaries() {
        ReportDateRange range = new ReportDateRange(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10));

        assertEquals(List.of(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2),
                        LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 4),
                        LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6),
                        LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 8),
                        LocalDate.of(2026, 1, 9), LocalDate.of(2026, 1, 10)),
                range.periodStarts(ReportGranularity.DAY, 10));
        assertEquals(List.of(
                        LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 5)),
                range.periodStarts(ReportGranularity.WEEK, 2));
        assertEquals(List.of(LocalDate.of(2026, 1, 1)),
                range.periodStarts(ReportGranularity.MONTH, 1));
    }

    @Test
    void rangeAndBucketBoundsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new ReportDateRange(
                LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1)));
        ReportDateRange range = new ReportDateRange(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 11));
        assertEquals(11L, range.inclusiveDays());
        assertThrows(IllegalArgumentException.class,
                () -> range.periodStarts(ReportGranularity.DAY, 10));
    }
}
