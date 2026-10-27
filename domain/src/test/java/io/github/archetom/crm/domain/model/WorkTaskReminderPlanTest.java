package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.WorkTaskDomainException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkTaskReminderPlanTest {

    @Test
    void dueTimeResolutionAndEscalationKindFollowTheFixedTimingRules() {
        LocalDateTime due = LocalDateTime.of(2026, 7, 22, 12, 0);
        WorkTaskReminderStep before = step(WorkTaskReminderTiming.BEFORE_DUE, 30);
        WorkTaskReminderStep at = step(WorkTaskReminderTiming.AT_DUE, 0);
        WorkTaskReminderStep overdue = step(WorkTaskReminderTiming.OVERDUE, 10);

        assertEquals(due.minusMinutes(30), before.dueAt(due));
        assertEquals(due, at.dueAt(due));
        assertEquals(due.plusMinutes(10), overdue.dueAt(due));
        assertEquals(NotificationKind.WORK_TASK_ESCALATION, overdue.notificationKind());
    }

    @Test
    void planRejectsInvalidOffsetsDuplicatesAndMoreThanEightSteps() {
        assertThrows(WorkTaskDomainException.class,
                () -> step(WorkTaskReminderTiming.AT_DUE, 1));
        WorkTaskReminderStep duplicate = step(WorkTaskReminderTiming.AT_DUE, 0);
        assertThrows(WorkTaskDomainException.class,
                () -> WorkTaskReminderPlan.of(List.of(duplicate, duplicate)));
        assertThrows(WorkTaskDomainException.class, () -> WorkTaskReminderPlan.of(IntStream.rangeClosed(1, 9)
                .mapToObj(offset -> step(WorkTaskReminderTiming.BEFORE_DUE, offset)).toList()));
    }

    @Test
    void derivedLedgerTimesMustFitMySqlDatetimeRange() {
        WorkTaskReminderPlan before = WorkTaskReminderPlan.of(List.of(
                step(WorkTaskReminderTiming.BEFORE_DUE, 1)));
        WorkTaskReminderPlan atDue = WorkTaskReminderPlan.of(List.of(
                step(WorkTaskReminderTiming.AT_DUE, 0)));

        assertThrows(WorkTaskDomainException.class, () -> before.validateForPersistence(
                LocalDateTime.of(1000, 1, 1, 0, 0), Duration.ofDays(1)));
        assertThrows(WorkTaskDomainException.class, () -> atDue.validateForPersistence(
                LocalDateTime.of(9999, 12, 31, 23, 59, 59), Duration.ofDays(1)));
    }

    private static WorkTaskReminderStep step(WorkTaskReminderTiming timing, int offset) {
        return new WorkTaskReminderStep(timing, offset,
                Set.of(WorkTaskReminderRecipient.ASSIGNEE));
    }
}
