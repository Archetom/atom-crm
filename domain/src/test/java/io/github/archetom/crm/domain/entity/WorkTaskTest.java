package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.WorkTaskDomainException;
import io.github.archetom.crm.domain.model.WorkTaskPriority;
import io.github.archetom.crm.domain.model.WorkTaskReminderPlan;
import io.github.archetom.crm.domain.model.WorkTaskReminderRecipient;
import io.github.archetom.crm.domain.model.WorkTaskReminderStep;
import io.github.archetom.crm.domain.model.WorkTaskReminderTiming;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.WorkTaskBusinessRecord;
import io.github.archetom.crm.domain.model.WorkTaskStatus;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkTaskTest {

    private static final TenantId TENANT = new TenantId(1L);
    private static final MemberId CREATOR = new MemberId(2L);
    private static final MemberId ASSIGNEE = new MemberId(3L);
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 19, 10, 0, 0, 123_456_789);

    @Test
    void stateMachineKeepsFirstStartAndServerLifecycleFacts() {
        WorkTask task = task();

        task.start(NOW.plusMinutes(1));
        LocalDateTime firstStarted = task.getFirstStartedTime();
        assertEquals(WorkTaskStatus.IN_PROGRESS, task.getStatus());
        assertEquals(1L, task.getWorklistProjectionVersion());
        assertEquals(123_456_000, firstStarted.getNano());

        task.returnToTodo(NOW.plusMinutes(2));
        task.start(NOW.plusMinutes(3));
        assertEquals(firstStarted, task.getFirstStartedTime());

        task.complete(NOW.plusMinutes(4));
        assertEquals(WorkTaskStatus.DONE, task.getStatus());
        assertEquals(NOW.plusMinutes(4).withNano(123_456_000),
                task.getCompletedTime());
        assertNull(task.getCancelledTime());
        assertThrows(WorkTaskDomainException.class,
                () -> task.returnToTodo(NOW.plusMinutes(5)));
        assertThrows(WorkTaskDomainException.class,
                () -> task.cancel(NOW.plusMinutes(5)));
    }

    @Test
    void projectionVersionOnlyTracksWorklistFieldsAndStatus() {
        WorkTask task = task();

        task.updateStructure("Task", "Changed description",
                WorkTaskPriority.NORMAL, NOW.plusDays(1), NOW.plusMinutes(1));
        assertEquals(0L, task.getWorklistProjectionVersion());

        task.updateStructure("Changed title", "Changed description",
                WorkTaskPriority.HIGH, NOW.plusDays(2), NOW.plusMinutes(2));
        assertEquals(1L, task.getWorklistProjectionVersion());

        task.reassign(new MemberId(4L), NOW.plusMinutes(3));
        assertEquals(2L, task.getWorklistProjectionVersion());

        task.cancel(NOW.plusMinutes(4));
        assertEquals(3L, task.getWorklistProjectionVersion());
        assertEquals(WorkTaskStatus.CANCELLED, task.getStatus());
    }

    @Test
    void deletionIsLimitedToNeverStartedUncommentedTodoTasks() {
        WorkTask deletable = task();
        deletable.delete(false, NOW.plusMinutes(1));
        assertEquals(WorkTaskStatus.DELETED, deletable.getStatus());

        WorkTask commented = task();
        assertThrows(WorkTaskDomainException.class,
                () -> commented.delete(true, NOW.plusMinutes(1)));

        WorkTask started = task();
        started.start(NOW.plusMinutes(1));
        started.returnToTodo(NOW.plusMinutes(2));
        assertThrows(WorkTaskDomainException.class,
                () -> started.delete(false, NOW.plusMinutes(3)));
    }

    @Test
    void textUsesUnicodeCodePointBoundsAndDueTimeDrivesOverdueProjection() {
        String title = "😀".repeat(WorkTask.MAX_TITLE_CODE_POINTS);
        String description = "😀".repeat(WorkTask.MAX_DESCRIPTION_CODE_POINTS);
        WorkTask task = WorkTask.create(TENANT, CREATOR, ASSIGNEE, title, description,
                null, NOW.minusSeconds(1), NOW);
        assertEquals(WorkTaskPriority.NORMAL, task.getPriority());
        assertEquals(description, task.getDescription());
        assertTrue(task.isOverdueAt(NOW));

        task.complete(NOW.plusSeconds(1));
        assertFalse(task.isOverdueAt(NOW.plusSeconds(2)));
        assertThrows(WorkTaskDomainException.class, () -> WorkTask.create(
                TENANT, CREATOR, ASSIGNEE, title + "😀", null,
                WorkTaskPriority.NORMAL, null, NOW));
        assertThrows(WorkTaskDomainException.class, () -> WorkTask.create(
                TENANT, CREATOR, ASSIGNEE, "Task", description + "😀",
                WorkTaskPriority.NORMAL, null, NOW));
        assertThrows(WorkTaskDomainException.class, () -> WorkTask.create(
                TENANT, CREATOR, ASSIGNEE, "Task", null,
                WorkTaskPriority.NORMAL, LocalDateTime.of(999, 1, 1, 0, 0), NOW));
    }

    @Test
    void reconstitutionRestoresVersionWithoutRelaxingTerminalTimeRules() {
        WorkTask restored = WorkTask.reconstitute(new WorkTask.Snapshot(
                new WorkTaskId(8L), TENANT, CREATOR, ASSIGNEE, Set.of(), null,
                "Persisted", null,
                WorkTaskPriority.URGENT, null, WorkTaskStatus.DONE, NOW,
                7L, NOW.plusHours(1), null, 12L, NOW, NOW.plusHours(1)));
        assertEquals(12L, restored.getVersion());
        assertEquals(7L, restored.getWorklistProjectionVersion());

        assertThrows(WorkTaskDomainException.class, () -> WorkTask.reconstitute(
                new WorkTask.Snapshot(new WorkTaskId(9L), TENANT, CREATOR,
                        ASSIGNEE, Set.of(), null, "Invalid", null,
                        WorkTaskPriority.NORMAL, null,
                        WorkTaskStatus.DONE, null, 1L, null, null,
                        0L, NOW, NOW)));
        assertThrows(WorkTaskDomainException.class, () -> WorkTask.reconstitute(
                new WorkTask.Snapshot(new WorkTaskId(10L), TENANT, CREATOR,
                        ASSIGNEE, Set.of(), null, "Invalid", null,
                        WorkTaskPriority.NORMAL, null,
                        WorkTaskStatus.IN_PROGRESS, null, 1L, null, null,
                        0L, NOW, NOW)));
    }

    @Test
    void followersAndBusinessRecordAreBoundedWithoutChangingWorklistProjection() {
        WorkTask task = task();
        task.replaceFollowers(List.of(new MemberId(4L), new MemberId(5L)),
                NOW.plusMinutes(1));
        task.updateBusinessRecord(new WorkTaskBusinessRecord(
                BusinessObjectType.CUSTOMER, 20L), NOW.plusMinutes(2));

        assertEquals(Set.of(new MemberId(4L), new MemberId(5L)),
                task.getFollowerMemberIds());
        assertEquals(BusinessObjectType.CUSTOMER,
                task.getBusinessRecord().objectType());
        assertEquals(0L, task.getWorklistProjectionVersion());
        assertThrows(WorkTaskDomainException.class,
                () -> task.replaceFollowers(List.of(new MemberId(4L),
                        new MemberId(4L)), NOW));
        assertThrows(WorkTaskDomainException.class,
                () -> task.replaceFollowers(java.util.stream.LongStream.rangeClosed(1, 51)
                        .mapToObj(MemberId::new).toList(), NOW));
        assertThrows(WorkTaskDomainException.class,
                () -> new WorkTaskBusinessRecord(BusinessObjectType.PRODUCT, 1L));
    }

    @Test
    void reminderPlanUsesItsOwnProjectionAndIsClearedWithTheDueTime() {
        WorkTask task = task();
        WorkTaskReminderPlan plan = WorkTaskReminderPlan.of(List.of(new WorkTaskReminderStep(
                WorkTaskReminderTiming.BEFORE_DUE, 30,
                Set.of(WorkTaskReminderRecipient.ASSIGNEE,
                        WorkTaskReminderRecipient.FOLLOWERS))));

        task.replaceReminderPlan(plan, NOW.plusMinutes(1));
        assertEquals(0L, task.getWorklistProjectionVersion());
        assertEquals(1L, task.getNotificationProjectionVersion());

        task.replaceFollowers(List.of(new MemberId(4L)), NOW.plusMinutes(2));
        assertEquals(0L, task.getWorklistProjectionVersion());
        assertEquals(2L, task.getNotificationProjectionVersion());

        task.updateStructure("Task", "Description", WorkTaskPriority.NORMAL, null,
                NOW.plusMinutes(3));
        assertTrue(task.getReminderPlan().isEmpty());
        assertEquals(1L, task.getWorklistProjectionVersion());
        assertEquals(3L, task.getNotificationProjectionVersion());
        assertThrows(WorkTaskDomainException.class,
                () -> task.replaceReminderPlan(plan, NOW.plusMinutes(4)));
    }

    private WorkTask task() {
        return WorkTask.create(TENANT, CREATOR, ASSIGNEE, "Task", "Description",
                WorkTaskPriority.NORMAL, NOW.plusDays(1), NOW);
    }
}
