package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.model.WorkRecurrenceFrequency;
import io.github.archetom.crm.domain.model.WorkRecurrenceRule;
import io.github.archetom.crm.domain.model.WorkTaskPriority;
import io.github.archetom.crm.domain.model.WorkTaskReminderPlan;
import io.github.archetom.crm.domain.model.WorkTaskReminderRecipient;
import io.github.archetom.crm.domain.model.WorkTaskReminderStep;
import io.github.archetom.crm.domain.model.WorkTaskReminderTiming;
import io.github.archetom.crm.domain.model.WorkTaskOrigin;
import io.github.archetom.crm.domain.model.WorkTaskSeriesStatus;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskSeriesId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkTaskSeriesTest {
    @Test
    void coalescesMissedTimesToOneLatestOccurrenceAndKeepsFutureCursor() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 1, 0, 0);
        WorkTaskSeries series = WorkTaskSeries.create(new TenantId(1L), new MemberId(2L), new MemberId(3L),
                List.of(new MemberId(4L)), "Renew", null, WorkTaskPriority.HIGH,
                new WorkRecurrenceRule(WorkRecurrenceFrequency.DAILY, 1, Set.of(), null, false,
                        "UTC", LocalDate.of(2026, 7, 1), LocalTime.of(9, 0), null), now);
        series.activate(now);

        WorkTaskSeries.DueOccurrence due = series.advanceToLatestDue(LocalDateTime.of(2026, 7, 4, 11, 0),
                LocalDateTime.of(2026, 7, 4, 11, 1));

        assertNotNull(due);
        assertEquals(LocalDateTime.of(2026, 7, 4, 9, 0), due.resolution().nominalLocalTime());
        assertEquals(3, due.coalescedSkippedCount());
        assertEquals(4, series.getOccurrenceCount());
        assertEquals(LocalDateTime.of(2026, 7, 5, 9, 0), series.getNextNominalLocalTime());
    }

    @Test
    void editCreatesFutureGenerationWithoutChangingPastFacts() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 1, 8, 0);
        WorkTaskSeries series = WorkTaskSeries.create(new TenantId(1L), new MemberId(2L), new MemberId(3L),
                List.of(), "Renew", null, WorkTaskPriority.NORMAL,
                new WorkRecurrenceRule(WorkRecurrenceFrequency.DAILY, 1, Set.of(), null, false,
                        "UTC", LocalDate.of(2026, 7, 1), LocalTime.of(9, 0), null), now);
        series.activate(now);
        series.advanceToLatestDue(LocalDateTime.of(2026, 7, 1, 10, 0), LocalDateTime.of(2026, 7, 1, 10, 0));
        long occurrenceCount = series.getOccurrenceCount();
        series.updateFuture(new MemberId(3L), List.of(), "Later", null, WorkTaskPriority.NORMAL,
                new WorkRecurrenceRule(WorkRecurrenceFrequency.WEEKLY, 1, Set.of(java.time.DayOfWeek.MONDAY), null, false,
                        "UTC", LocalDate.of(2026, 7, 6), LocalTime.of(10, 0), null), LocalDateTime.of(2026, 7, 2, 0, 0));

        assertEquals(2, series.getGeneration());
        assertEquals(occurrenceCount, series.getOccurrenceCount());
        assertEquals(WorkTaskSeriesStatus.ACTIVE, series.getStatus());
        assertEquals(LocalDateTime.of(2026, 7, 6, 10, 0), series.getNextNominalLocalTime());
    }

    @Test
    void generatedTaskKeepsOriginSnapshotWhenAnOrdinaryTaskDueTimeChanges() {
        LocalDateTime originalDue = LocalDateTime.of(2026, 7, 1, 9, 0);
        WorkTask task = WorkTask.createRecurring(new TenantId(1L), new MemberId(2L), new MemberId(3L), List.of(),
                "Renew", null, WorkTaskPriority.NORMAL, originalDue,
                WorkTaskOrigin.recurrence(11L, 1L, 1L, originalDue, "UTC", 0, originalDue),
                LocalDateTime.of(2026, 7, 1, 8, 0));

        task.updateStructure("Renew", null, WorkTaskPriority.NORMAL,
                LocalDateTime.of(2026, 7, 2, 9, 0), LocalDateTime.of(2026, 7, 1, 8, 1));

        assertEquals(originalDue, task.getOrigin().resolvedDueTime());
        assertEquals(LocalDateTime.of(2026, 7, 2, 9, 0), task.getDueTime());
    }

    @Test
    void overlapOccurrenceIsDueByResolvedUtcRatherThanRepeatedWallClock() {
        WorkTaskSeries series = WorkTaskSeries.create(new TenantId(1L), new MemberId(2L), new MemberId(3L),
                List.of(), "Renew", null, WorkTaskPriority.NORMAL,
                new WorkRecurrenceRule(WorkRecurrenceFrequency.DAILY, 1, Set.of(), null, false,
                        "America/New_York", LocalDate.of(2026, 11, 1), LocalTime.of(1, 30), null),
                LocalDateTime.of(2026, 11, 1, 0, 0));
        series.activate(LocalDateTime.of(2026, 11, 1, 0, 0));

        WorkTaskSeries.DueOccurrence due = series.advanceToLatestDue(
                LocalDateTime.of(2026, 11, 1, 6, 15), LocalDateTime.of(2026, 11, 1, 6, 16));

        assertNotNull(due);
        assertEquals(LocalDateTime.of(2026, 11, 1, 5, 30), due.resolution().resolvedUtcTime());
    }

    @Test
    void occurrenceCapStopsCoalescingAtTheLastPermittedOccurrence() {
        WorkRecurrenceRule rule = new WorkRecurrenceRule(WorkRecurrenceFrequency.DAILY, 1, Set.of(), null, false,
                "UTC", LocalDate.of(2026, 7, 1), LocalTime.of(9, 0), null);
        WorkTaskSeries series = WorkTaskSeries.reconstitute(new WorkTaskSeries.Snapshot(
                new WorkTaskSeriesId(1L), new TenantId(1L), new MemberId(2L), new MemberId(3L), Set.of(),
                "Renew", null, WorkTaskPriority.NORMAL, rule, WorkTaskSeriesStatus.ACTIVE,
                1, 10_000, LocalDateTime.of(2026, 7, 2, 9, 0), LocalDateTime.of(2026, 7, 2, 9, 0),
                9_999, 0, null, 0L,
                LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0)));

        WorkTaskSeries.DueOccurrence due = series.advanceToLatestDue(
                LocalDateTime.of(2026, 7, 4, 12, 0), LocalDateTime.of(2026, 7, 4, 12, 1));

        assertNotNull(due);
        assertEquals(10_000, due.sequence());
        assertEquals(0, due.coalescedSkippedCount());
        assertEquals(10_000, series.getOccurrenceCount());
        assertEquals(WorkTaskSeriesStatus.ENDED, series.getStatus());
    }

    @Test
    void reactivationDuringOverlapSkipsTheEarlierOffsetOccurrenceAlreadyInThePast() {
        WorkTaskSeries series = WorkTaskSeries.create(new TenantId(1L), new MemberId(2L), new MemberId(3L),
                List.of(), "Renew", null, WorkTaskPriority.NORMAL,
                new WorkRecurrenceRule(WorkRecurrenceFrequency.DAILY, 1, Set.of(), null, false,
                        "America/New_York", LocalDate.of(2026, 11, 1), LocalTime.of(1, 30), null),
                LocalDateTime.of(2026, 11, 1, 0, 0));
        series.activate(LocalDateTime.of(2026, 11, 1, 0, 0));
        series.pause(LocalDateTime.of(2026, 11, 1, 0, 1), "PAUSED");

        series.activate(LocalDateTime.of(2026, 11, 1, 6, 15));

        assertEquals(LocalDateTime.of(2026, 11, 2, 1, 30), series.getNextNominalLocalTime());
    }

    @Test
    void endsAtPersistenceBoundaryRatherThanSavingAYear10000Cursor() {
        LocalDateTime created = LocalDateTime.of(9999, 12, 31, 8, 0);
        WorkTaskSeries series = WorkTaskSeries.create(new TenantId(1L), new MemberId(2L), new MemberId(3L),
                List.of(), "Renew", null, WorkTaskPriority.NORMAL,
                new WorkRecurrenceRule(WorkRecurrenceFrequency.DAILY, 1, Set.of(), null, false,
                        "UTC", LocalDate.of(9999, 12, 31), LocalTime.of(9, 0), null), created);
        series.activate(created);

        WorkTaskSeries.DueOccurrence due = series.advanceToLatestDue(
                LocalDateTime.of(9999, 12, 31, 10, 0), LocalDateTime.of(9999, 12, 31, 10, 1));

        assertNotNull(due);
        assertEquals(WorkTaskSeriesStatus.ENDED, series.getStatus());
        assertNull(series.getNextNominalLocalTime());
    }

    @Test
    void editingADraftOrPausedTemplateWithNoFutureOccurrenceDoesNotEndIt() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 2, 12, 0);
        WorkRecurrenceRule futureRule = daily(LocalDate.of(2026, 7, 3), null);
        WorkRecurrenceRule expiredRule = daily(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));
        WorkTaskSeries draft = series(futureRule, now);
        WorkTaskSeries paused = series(futureRule, now);
        paused.activate(now);
        paused.pause(now, "PAUSED");

        draft.updateFuture(new MemberId(3L), List.of(), "Renew", null, WorkTaskPriority.NORMAL, expiredRule, now);
        paused.updateFuture(new MemberId(3L), List.of(), "Renew", null, WorkTaskPriority.NORMAL, expiredRule, now);

        assertEquals(WorkTaskSeriesStatus.DRAFT, draft.getStatus());
        assertEquals(WorkTaskSeriesStatus.PAUSED, paused.getStatus());
        assertNull(draft.getNextNominalLocalTime());
        assertNull(paused.getNextNominalLocalTime());
        assertEquals("TIME_RANGE", draft.getLastSafeReason());
        assertEquals("TIME_RANGE", paused.getLastSafeReason());
    }

    @Test
    void activationWithNoFutureOccurrenceFailsWithoutChangingTheTemplateLifecycle() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 2, 12, 0);
        WorkTaskSeries series = series(daily(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1)), now);

        assertThrows(io.github.archetom.crm.domain.exception.WorkTaskDomainException.class,
                () -> series.activate(now));

        assertEquals(WorkTaskSeriesStatus.DRAFT, series.getStatus());
        assertEquals(LocalDateTime.of(2026, 7, 1, 9, 0), series.getNextNominalLocalTime());
    }

    @Test
    void reminderTemplateStartsANewFutureGeneration() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 2, 12, 0);
        WorkTaskSeries series = series(daily(LocalDate.of(2026, 7, 3), null), now);
        WorkTaskReminderPlan plan = WorkTaskReminderPlan.of(List.of(new WorkTaskReminderStep(
                WorkTaskReminderTiming.AT_DUE, 0,
                Set.of(WorkTaskReminderRecipient.ASSIGNEE))));

        series.replaceReminderPlanTemplate(plan, now.plusMinutes(1));

        assertEquals(plan, series.getReminderPlan());
        assertEquals(2L, series.getGeneration());
    }

    private static WorkTaskSeries series(WorkRecurrenceRule rule, LocalDateTime now) {
        return WorkTaskSeries.create(new TenantId(1L), new MemberId(2L), new MemberId(3L),
                List.of(), "Renew", null, WorkTaskPriority.NORMAL, rule, now);
    }

    private static WorkRecurrenceRule daily(LocalDate start, LocalDate end) {
        return new WorkRecurrenceRule(WorkRecurrenceFrequency.DAILY, 1, Set.of(), null, false,
                "UTC", start, LocalTime.of(9, 0), end);
    }
}
