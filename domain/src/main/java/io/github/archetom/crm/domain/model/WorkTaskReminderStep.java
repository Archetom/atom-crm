package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.WorkTaskDomainException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/** A bounded, fixed-shape reminder instruction owned by a Work task or series. */
public record WorkTaskReminderStep(
        WorkTaskReminderTiming timing,
        int offsetMinutes,
        Set<WorkTaskReminderRecipient> recipients) {

    public static final int MAX_OFFSET_MINUTES = 30 * 24 * 60;

    public WorkTaskReminderStep {
        if (timing == null || recipients == null || recipients.isEmpty()
                || recipients.stream().anyMatch(java.util.Objects::isNull)) {
            throw new WorkTaskDomainException("work task reminder step is invalid");
        }
        recipients = Set.copyOf(recipients);
        boolean validOffset = switch (timing) {
            case BEFORE_DUE, OVERDUE -> offsetMinutes >= 1
                    && offsetMinutes <= MAX_OFFSET_MINUTES;
            case AT_DUE -> offsetMinutes == 0;
        };
        if (!validOffset) {
            throw new WorkTaskDomainException("work task reminder offset is invalid");
        }
    }

    /** Stable durable identity; target order cannot change it. */
    public String stepKey() {
        return timing.name() + "_" + offsetMinutes + "_" + recipients.stream()
                .sorted(Comparator.comparing(Enum::name)).map(Enum::name)
                .collect(Collectors.joining("_"));
    }

    public LocalDateTime dueAt(LocalDateTime taskDueTime) {
        if (taskDueTime == null) {
            throw new WorkTaskDomainException("work task reminder requires a due time");
        }
        return switch (timing) {
            case BEFORE_DUE -> taskDueTime.minusMinutes(offsetMinutes);
            case AT_DUE -> taskDueTime;
            case OVERDUE -> taskDueTime.plusMinutes(offsetMinutes);
        };
    }

    public NotificationKind notificationKind() {
        return timing == WorkTaskReminderTiming.OVERDUE
                ? NotificationKind.WORK_TASK_ESCALATION
                : NotificationKind.WORK_TASK_REMINDER;
    }
}
