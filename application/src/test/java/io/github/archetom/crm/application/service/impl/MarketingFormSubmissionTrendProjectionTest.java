package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.domain.model.MarketingFormSubmissionTrendPoint;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketingFormSubmissionTrendProjectionTest {

    private static final LocalDate FIRST = LocalDate.of(2026, 7, 17);
    private static final LocalDate SECOND = LocalDate.of(2026, 7, 18);
    private static final LocalDate THIRD = LocalDate.of(2026, 7, 19);
    private static final List<LocalDate> PERIODS = List.of(FIRST, SECOND, THIRD);

    @Test
    void fillsMissingPeriodsInStableOrder() {
        assertEquals(List.of(
                        new MarketingFormSubmissionTrendPoint(FIRST, 0L),
                        new MarketingFormSubmissionTrendPoint(SECOND, 2L),
                        new MarketingFormSubmissionTrendPoint(THIRD, 0L)),
                MarketingFormSubmissionTrendProjection.complete(
                        PERIODS, List.of(
                                new MarketingFormSubmissionTrendPoint(SECOND, 2L))));
    }

    @Test
    void rejectsUnexpectedOrDuplicateDatabasePeriods() {
        MarketingFormSubmissionTrendPoint second =
                new MarketingFormSubmissionTrendPoint(SECOND, 1L);

        assertThrows(IllegalStateException.class,
                () -> MarketingFormSubmissionTrendProjection.complete(
                        PERIODS, List.of(second, second)));
        assertThrows(IllegalStateException.class,
                () -> MarketingFormSubmissionTrendProjection.complete(
                        PERIODS, List.of(new MarketingFormSubmissionTrendPoint(
                                THIRD.plusDays(1), 1L))));
    }
}
