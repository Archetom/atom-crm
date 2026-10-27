package io.github.archetom.crm.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketingFormSubmissionDateRangeTest {

    @Test
    void createsIsoWeekAndMonthPeriodStarts() {
        MarketingFormSubmissionDateRange range = new MarketingFormSubmissionDateRange(
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 2));

        assertEquals(List.of(
                        LocalDate.of(2026, 1, 26),
                        LocalDate.of(2026, 2, 2),
                        LocalDate.of(2026, 2, 9),
                        LocalDate.of(2026, 2, 16),
                        LocalDate.of(2026, 2, 23),
                        LocalDate.of(2026, 3, 2)),
                range.periodStarts(ReportGranularity.WEEK, 120));
        assertEquals(List.of(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 3, 1)),
                range.periodStarts(ReportGranularity.MONTH, 120));
    }

    @Test
    void rejectsInvalidRangeAndBucketOverflow() {
        assertThrows(IllegalArgumentException.class,
                () -> new MarketingFormSubmissionDateRange(
                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31)));
        MarketingFormSubmissionDateRange range = new MarketingFormSubmissionDateRange(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));
        assertThrows(IllegalArgumentException.class,
                () -> range.periodStarts(ReportGranularity.DAY, 2));
    }
}
