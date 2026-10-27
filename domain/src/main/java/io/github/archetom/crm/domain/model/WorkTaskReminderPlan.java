package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.WorkTaskDomainException;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Typed reminder policy, deliberately not a cron expression or a payload template. */
public record WorkTaskReminderPlan(List<WorkTaskReminderStep> steps) {

    public static final int MAX_STEPS = 8;
    private static final LocalDateTime MYSQL_DATETIME_MIN = LocalDateTime.of(1000, 1, 1, 0, 0);
    private static final LocalDateTime MYSQL_DATETIME_MAX =
            LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_000);

    public WorkTaskReminderPlan {
        if (steps == null || steps.size() > MAX_STEPS || steps.stream().anyMatch(java.util.Objects::isNull)) {
            throw new WorkTaskDomainException("work task reminder plan is invalid");
        }
        List<WorkTaskReminderStep> normalized = steps.stream()
                .sorted(Comparator.comparing(WorkTaskReminderStep::stepKey)).toList();
        if (normalized.stream().map(WorkTaskReminderStep::stepKey).distinct().count()
                != normalized.size()) {
            throw new WorkTaskDomainException("work task reminder steps must be unique");
        }
        steps = List.copyOf(normalized);
    }

    public static WorkTaskReminderPlan empty() {
        return new WorkTaskReminderPlan(List.of());
    }

    public static WorkTaskReminderPlan of(Collection<WorkTaskReminderStep> steps) {
        return new WorkTaskReminderPlan(steps == null ? List.of() : List.copyOf(steps));
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public WorkTaskReminderStep requireStep(String stepKey) {
        return steps.stream().filter(step -> step.stepKey().equals(stepKey)).findFirst()
                .orElseThrow(() -> new WorkTaskDomainException("work task reminder step was not found"));
    }

    /**
     * Validates dates derived for the durable occurrence ledger. An unplanned task
     * has no due time and therefore no persisted reminder date yet.
     */
    public void validateForPersistence(LocalDateTime taskDueTime, Duration retryDeadline) {
        if (isEmpty() || taskDueTime == null) return;
        if (retryDeadline == null || retryDeadline.isNegative() || retryDeadline.isZero()) {
            throw new WorkTaskDomainException("work task reminder retry deadline is invalid");
        }
        for (WorkTaskReminderStep step : steps) {
            LocalDateTime dueTime;
            LocalDateTime deadlineAt;
            try {
                dueTime = step.dueAt(taskDueTime);
                deadlineAt = dueTime.plus(retryDeadline);
            } catch (DateTimeException | ArithmeticException exception) {
                throw new WorkTaskDomainException("work task reminder time is out of range");
            }
            if (outsideMySqlDatetime(dueTime) || outsideMySqlDatetime(deadlineAt)) {
                throw new WorkTaskDomainException("work task reminder time is out of range");
            }
        }
    }

    private static boolean outsideMySqlDatetime(LocalDateTime value) {
        return value.isBefore(MYSQL_DATETIME_MIN) || value.isAfter(MYSQL_DATETIME_MAX);
    }
}
