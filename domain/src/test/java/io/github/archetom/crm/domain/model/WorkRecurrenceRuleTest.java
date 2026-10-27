package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.WorkRecurrenceTimeRangeExceededException;
import io.github.archetom.crm.domain.exception.WorkTaskDomainException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkRecurrenceRuleTest {
    @Test
    void monthlyAnchorClampsButKeepsOriginalDay() {
        WorkRecurrenceRule rule = new WorkRecurrenceRule(WorkRecurrenceFrequency.MONTHLY, 1, Set.of(),
                31, false, "UTC", LocalDate.of(2026, 1, 31), LocalTime.of(9, 0), null);

        assertEquals(LocalDateTime.of(2026, 2, 28, 9, 0), rule.nextNominal(rule.firstNominal()));
        assertEquals(LocalDateTime.of(2026, 3, 31, 9, 0),
                rule.nextNominal(LocalDateTime.of(2026, 2, 28, 9, 0)));
    }

    @Test
    void resolvesGapForwardAndOverlapWithEarlierOffset() {
        WorkRecurrenceRule gap = new WorkRecurrenceRule(WorkRecurrenceFrequency.DAILY, 1, Set.of(), null,
                false, "America/New_York", LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), null);
        WorkRecurrenceResolution shifted = gap.resolve(gap.firstNominal());
        assertEquals(LocalDateTime.of(2026, 3, 8, 3, 0), shifted.resolvedLocalTime());
        assertEquals(-4 * 3600, shifted.offsetSeconds());

        WorkRecurrenceRule overlap = new WorkRecurrenceRule(WorkRecurrenceFrequency.DAILY, 1, Set.of(), null,
                false, "America/New_York", LocalDate.of(2026, 11, 1), LocalTime.of(1, 30), null);
        assertEquals(-4 * 3600, overlap.resolve(overlap.firstNominal()).offsetSeconds());
    }

    @Test
    void weeklyRequiresAClosedNonEmptyDaySet() {
        assertThrows(RuntimeException.class, () -> new WorkRecurrenceRule(WorkRecurrenceFrequency.WEEKLY,
                1, Set.of(), null, false, "UTC", LocalDate.of(2026, 1, 1), LocalTime.NOON, null));
        WorkRecurrenceRule rule = new WorkRecurrenceRule(WorkRecurrenceFrequency.WEEKLY, 2,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), null, false, "UTC",
                LocalDate.of(2026, 1, 5), LocalTime.NOON, null);
        assertEquals(LocalDateTime.of(2026, 1, 19, 12, 0),
                rule.nominalAtOrAfter(LocalDateTime.of(2026, 1, 10, 0, 0)));
        assertFalse(rule.isWithinEnd(null));
    }

    @Test
    void weeklyAndMonthlyFirstOccurrencesHonorTheirClosedRuleParameters() {
        WorkRecurrenceRule weekly = new WorkRecurrenceRule(WorkRecurrenceFrequency.WEEKLY, 2,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), null, false, "UTC",
                LocalDate.of(2026, 1, 7), LocalTime.NOON, null);
        assertEquals(LocalDateTime.of(2026, 1, 9, 12, 0), weekly.firstNominal());
        assertEquals(LocalDateTime.of(2026, 1, 19, 12, 0), weekly.nextNominal(weekly.firstNominal()));

        WorkRecurrenceRule monthly = new WorkRecurrenceRule(WorkRecurrenceFrequency.MONTHLY, 1, Set.of(),
                5, false, "UTC", LocalDate.of(2026, 1, 7), LocalTime.NOON, null);
        assertEquals(LocalDateTime.of(2026, 2, 5, 12, 0), monthly.firstNominal());
    }

    @Test
    void rejectsSecondLevelOrSubsecondScheduleTimes() {
        assertThrows(RuntimeException.class, () -> new WorkRecurrenceRule(WorkRecurrenceFrequency.DAILY,
                1, Set.of(), null, false, "UTC", LocalDate.of(2026, 1, 1),
                LocalTime.of(9, 0, 1), null));
    }

    @Test
    void identifiesResolvedUtcValuesOutsideThePersistentRange() {
        WorkRecurrenceRule rule = new WorkRecurrenceRule(WorkRecurrenceFrequency.DAILY, 1, Set.of(), null,
                false, "Pacific/Pago_Pago", LocalDate.of(9999, 12, 31), LocalTime.of(23, 30), null);

        assertThrows(WorkRecurrenceTimeRangeExceededException.class, () -> rule.resolve(rule.firstNominal()));
        assertFalse(rule.nominalAtOrAfterPersistable(rule.firstNominal()).isPresent());
    }

    @Test
    void doesNotTreatRuleOrTimezoneFaultsAsNaturalEndOfSeries() {
        WorkRecurrenceRule rule = new WorkRecurrenceRule(WorkRecurrenceFrequency.DAILY, 1, Set.of(), null,
                false, "UTC", LocalDate.of(2026, 1, 1), LocalTime.NOON, null);

        assertThrows(WorkTaskDomainException.class, () -> rule.nextNominalPersistable(null));
    }

    @Test
    void rejectsFixedOffsetsAndNonOccurrencesRatherThanResolvingThem() {
        assertThrows(WorkTaskDomainException.class, () -> new WorkRecurrenceRule(
                WorkRecurrenceFrequency.DAILY, 1, Set.of(), null, false, "+08:00",
                LocalDate.of(2026, 1, 1), LocalTime.NOON, null));
        WorkRecurrenceRule rule = new WorkRecurrenceRule(WorkRecurrenceFrequency.DAILY, 1, Set.of(), null,
                false, "UTC", LocalDate.of(2026, 1, 1), LocalTime.of(9, 0), null);

        assertTrue(rule.isNominalOccurrence(LocalDateTime.of(2026, 1, 2, 9, 0)));
        assertFalse(rule.isNominalOccurrence(LocalDateTime.of(2026, 1, 2, 9, 1)));
        assertThrows(WorkTaskDomainException.class, () -> rule.resolve(LocalDateTime.of(2026, 1, 2, 9, 1)));
    }

    @Test
    void endsAllFrequenciesCleanlyAtThePersistentCalendarUpperBound() {
        LocalDate finalDate = LocalDate.of(9999, 12, 31);
        WorkRecurrenceRule daily = new WorkRecurrenceRule(WorkRecurrenceFrequency.DAILY, 1, Set.of(), null,
                false, "UTC", finalDate, LocalTime.of(9, 0), null);
        WorkRecurrenceRule weekly = new WorkRecurrenceRule(WorkRecurrenceFrequency.WEEKLY, 1,
                Set.of(finalDate.getDayOfWeek()), null, false, "UTC", finalDate, LocalTime.of(9, 0), null);
        WorkRecurrenceRule monthly = new WorkRecurrenceRule(WorkRecurrenceFrequency.MONTHLY, 1, Set.of(),
                31, false, "UTC", finalDate, LocalTime.of(9, 0), null);

        assertFalse(daily.nextNominalPersistable(daily.firstNominal()).isPresent());
        assertFalse(weekly.nextNominalPersistable(weekly.firstNominal()).isPresent());
        assertFalse(monthly.nextNominalPersistable(monthly.firstNominal()).isPresent());
    }
}
