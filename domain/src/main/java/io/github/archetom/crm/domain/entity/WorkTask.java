package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.WorkTaskDomainException;
import io.github.archetom.crm.domain.model.WorkTaskBusinessRecord;
import io.github.archetom.crm.domain.model.WorkTaskOrigin;
import io.github.archetom.crm.domain.model.WorkTaskPriority;
import io.github.archetom.crm.domain.model.WorkTaskReminderPlan;
import io.github.archetom.crm.domain.model.WorkTaskStatus;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** A tenant task with exactly one current assignee, whether manual or recurrence-generated. */
@Getter
public class WorkTask extends AggregateRoot<WorkTaskId> {

    public static final int MAX_TITLE_CODE_POINTS = 200;
    public static final int MAX_DESCRIPTION_CODE_POINTS = 10_000;
    public static final int MAX_FOLLOWERS = 50;

    private WorkTaskId id;
    private TenantId tenantId;
    private MemberId createdByMemberId;
    private MemberId currentAssigneeMemberId;
    private Set<MemberId> followerMemberIds = Set.of();
    private WorkTaskBusinessRecord businessRecord;
    private WorkTaskOrigin origin;
    private String title;
    private String description;
    private WorkTaskPriority priority;
    private LocalDateTime dueTime;
    private WorkTaskStatus status;
    private LocalDateTime firstStartedTime;
    private long worklistProjectionVersion;
    /** Independent reminder/inbox projection identity; it is not the worklist version. */
    private long notificationProjectionVersion;
    private WorkTaskReminderPlan reminderPlan = WorkTaskReminderPlan.empty();
    private LocalDateTime completedTime;
    private LocalDateTime cancelledTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private WorkTask() {
    }

    public record Snapshot(
            WorkTaskId id,
            TenantId tenantId,
            MemberId createdByMemberId,
            MemberId currentAssigneeMemberId,
            Set<MemberId> followerMemberIds,
            WorkTaskBusinessRecord businessRecord,
            String title,
            String description,
            WorkTaskPriority priority,
            LocalDateTime dueTime,
            WorkTaskStatus status,
            LocalDateTime firstStartedTime,
            long worklistProjectionVersion,
            long notificationProjectionVersion,
            WorkTaskReminderPlan reminderPlan,
            LocalDateTime completedTime,
            LocalDateTime cancelledTime,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime,
            WorkTaskOrigin origin
    ) {
        /** Source-compatible reconstruction shape used by pre-B27 persistence tests. */
        public Snapshot(
                WorkTaskId id, TenantId tenantId, MemberId createdByMemberId,
                MemberId currentAssigneeMemberId, Set<MemberId> followerMemberIds,
                WorkTaskBusinessRecord businessRecord, String title, String description,
                WorkTaskPriority priority, LocalDateTime dueTime, WorkTaskStatus status,
                LocalDateTime firstStartedTime, long worklistProjectionVersion,
                LocalDateTime completedTime, LocalDateTime cancelledTime, Long version,
                LocalDateTime createdTime, LocalDateTime updatedTime) {
            this(id, tenantId, createdByMemberId, currentAssigneeMemberId, followerMemberIds,
                    businessRecord, title, description, priority, dueTime, status,
                    firstStartedTime, worklistProjectionVersion, 0, WorkTaskReminderPlan.empty(),
                    completedTime, cancelledTime,
                    version, createdTime, updatedTime, WorkTaskOrigin.manual());
        }

        /** Source-compatible shape used by the B27a recurrence converter. */
        public Snapshot(
                WorkTaskId id, TenantId tenantId, MemberId createdByMemberId,
                MemberId currentAssigneeMemberId, Set<MemberId> followerMemberIds,
                WorkTaskBusinessRecord businessRecord, String title, String description,
                WorkTaskPriority priority, LocalDateTime dueTime, WorkTaskStatus status,
                LocalDateTime firstStartedTime, long worklistProjectionVersion,
                LocalDateTime completedTime, LocalDateTime cancelledTime, Long version,
                LocalDateTime createdTime, LocalDateTime updatedTime, WorkTaskOrigin origin) {
            this(id, tenantId, createdByMemberId, currentAssigneeMemberId, followerMemberIds,
                    businessRecord, title, description, priority, dueTime, status,
                    firstStartedTime, worklistProjectionVersion, 0, WorkTaskReminderPlan.empty(),
                    completedTime, cancelledTime, version, createdTime, updatedTime, origin);
        }
    }

    public static WorkTask create(
            TenantId tenantId, MemberId createdByMemberId,
            MemberId currentAssigneeMemberId, String title, String description,
            WorkTaskPriority priority, LocalDateTime dueTime, LocalDateTime now) {
        WorkTask task = new WorkTask();
        task.tenantId = requireTenant(tenantId);
        task.createdByMemberId = requireMember(createdByMemberId, "work task creator");
        task.currentAssigneeMemberId = requireMember(
                currentAssigneeMemberId, "work task assignee");
        task.followerMemberIds = Set.of();
        task.origin = WorkTaskOrigin.manual();
        task.title = requiredText(title, MAX_TITLE_CODE_POINTS, "work task title");
        task.description = optionalText(
                description, MAX_DESCRIPTION_CODE_POINTS, "work task description");
        task.priority = priority == null ? WorkTaskPriority.NORMAL : priority;
        task.dueTime = optionalTime(dueTime, "work task due time");
        task.status = WorkTaskStatus.TODO;
        task.worklistProjectionVersion = 0;
        task.notificationProjectionVersion = 0;
        task.reminderPlan = WorkTaskReminderPlan.empty();
        task.createdTime = requiredTime(now, "work task creation time");
        task.updatedTime = task.createdTime;
        return task;
    }

    /** Internal B27 creation path. It does not accept a caller or public request shape. */
    public static WorkTask createRecurring(
            TenantId tenantId, MemberId createdByMemberId, MemberId currentAssigneeMemberId,
            Collection<MemberId> followers, String title, String description,
            WorkTaskPriority priority, LocalDateTime dueTime, WorkTaskOrigin origin,
            LocalDateTime now) {
        if (origin == null || !origin.isRecurring()) {
            throw new WorkTaskDomainException("recurring work task origin is required");
        }
        WorkTask task = create(tenantId, createdByMemberId, currentAssigneeMemberId,
                title, description, priority, dueTime, now);
        if (!java.util.Objects.equals(task.dueTime, origin.resolvedDueTime())) {
            throw new WorkTaskDomainException("recurring work task due time must match origin");
        }
        task.followerMemberIds = followers(followers);
        task.origin = origin;
        return task;
    }

    public static WorkTask reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new WorkTaskDomainException("work task snapshot is required");
        }
        WorkTask task = new WorkTask();
        task.id = snapshot.id();
        task.tenantId = requireTenant(snapshot.tenantId());
        task.createdByMemberId = requireMember(
                snapshot.createdByMemberId(), "work task creator");
        task.currentAssigneeMemberId = requireMember(
                snapshot.currentAssigneeMemberId(), "work task assignee");
        task.followerMemberIds = followers(snapshot.followerMemberIds());
        task.businessRecord = snapshot.businessRecord();
        task.origin = snapshot.origin() == null ? WorkTaskOrigin.manual() : snapshot.origin();
        task.title = requiredText(
                snapshot.title(), MAX_TITLE_CODE_POINTS, "work task title");
        task.description = optionalText(snapshot.description(),
                MAX_DESCRIPTION_CODE_POINTS, "work task description");
        task.priority = snapshot.priority() == null
                ? WorkTaskPriority.NORMAL : snapshot.priority();
        task.dueTime = optionalTime(snapshot.dueTime(), "work task due time");
        task.status = requireStatus(snapshot.status());
        task.firstStartedTime = optionalTime(
                snapshot.firstStartedTime(), "work task first started time");
        if (snapshot.worklistProjectionVersion() < 0) {
            throw new WorkTaskDomainException(
                    "work task worklist projection version is invalid");
        }
        task.worklistProjectionVersion = snapshot.worklistProjectionVersion();
        if (snapshot.notificationProjectionVersion() < 0) {
            throw new WorkTaskDomainException(
                    "work task notification projection version is invalid");
        }
        task.notificationProjectionVersion = snapshot.notificationProjectionVersion();
        task.reminderPlan = snapshot.reminderPlan() == null
                ? WorkTaskReminderPlan.empty() : snapshot.reminderPlan();
        if (task.dueTime == null && !task.reminderPlan.isEmpty()) {
            throw new WorkTaskDomainException("work task reminder requires a due time");
        }
        task.completedTime = optionalTime(snapshot.completedTime(), "work task completed time");
        task.cancelledTime = optionalTime(snapshot.cancelledTime(), "work task cancelled time");
        task.validateLifecycleTimes();
        task.restoreVersion(snapshot.version());
        task.createdTime = optionalTime(snapshot.createdTime(), "work task creation time");
        task.updatedTime = optionalTime(snapshot.updatedTime(), "work task update time");
        return task;
    }

    public void updateStructure(
            String title, String description, WorkTaskPriority priority,
            LocalDateTime dueTime, LocalDateTime now) {
        ensureOpen();
        String nextTitle = requiredText(title, MAX_TITLE_CODE_POINTS, "work task title");
        String nextDescription = optionalText(
                description, MAX_DESCRIPTION_CODE_POINTS, "work task description");
        WorkTaskPriority nextPriority = priority == null
                ? WorkTaskPriority.NORMAL : priority;
        LocalDateTime nextDueTime = optionalTime(dueTime, "work task due time");
        boolean worklistProjectionChanged = !this.title.equals(nextTitle)
                || this.priority != nextPriority
                || !java.util.Objects.equals(this.dueTime, nextDueTime);
        boolean notificationProjectionChanged = worklistProjectionChanged;
        this.title = nextTitle;
        this.description = nextDescription;
        this.priority = nextPriority;
        this.dueTime = nextDueTime;
        if (nextDueTime == null && !reminderPlan.isEmpty()) {
            reminderPlan = WorkTaskReminderPlan.empty();
            notificationProjectionChanged = true;
        }
        if (worklistProjectionChanged) {
            incrementProjectionVersion();
        }
        if (notificationProjectionChanged) incrementNotificationProjectionVersion();
        touch(now);
    }

    public void reassign(MemberId assignee, LocalDateTime now) {
        ensureOpen();
        MemberId next = requireMember(assignee, "work task assignee");
        if (!currentAssigneeMemberId.sameValueAs(next)) {
            currentAssigneeMemberId = next;
            incrementProjectionVersion();
            incrementNotificationProjectionVersion();
        }
        touch(now);
    }

    public void replaceFollowers(Collection<MemberId> followers, LocalDateTime now) {
        ensureOpen();
        Set<MemberId> next = followers(followers);
        if (!followerMemberIds.equals(next)) {
            followerMemberIds = next;
            incrementNotificationProjectionVersion();
        }
        touch(now);
    }

    /** Replaces the local reminder policy; only an open due task can retain active steps. */
    public void replaceReminderPlan(WorkTaskReminderPlan nextPlan, LocalDateTime now) {
        ensureOpen();
        WorkTaskReminderPlan next = nextPlan == null ? WorkTaskReminderPlan.empty() : nextPlan;
        if (dueTime == null && !next.isEmpty()) {
            throw new WorkTaskDomainException("work task reminder requires a due time");
        }
        if (!reminderPlan.equals(next)) {
            reminderPlan = next;
            incrementNotificationProjectionVersion();
        }
        touch(now);
    }

    public void updateBusinessRecord(
            WorkTaskBusinessRecord nextBusinessRecord, LocalDateTime now) {
        ensureOpen();
        businessRecord = nextBusinessRecord;
        touch(now);
    }

    public boolean isFollowedBy(MemberId memberId) {
        return memberId != null && followerMemberIds.contains(memberId);
    }

    public void start(LocalDateTime now) {
        if (status != WorkTaskStatus.TODO) {
            throw new WorkTaskDomainException("only todo work tasks can be started");
        }
        LocalDateTime actual = requiredTime(now, "work task start time");
        status = WorkTaskStatus.IN_PROGRESS;
        if (firstStartedTime == null) {
            firstStartedTime = actual;
        }
        incrementProjectionVersion();
        incrementNotificationProjectionVersion();
        touch(actual);
    }

    public void returnToTodo(LocalDateTime now) {
        if (status != WorkTaskStatus.IN_PROGRESS) {
            throw new WorkTaskDomainException(
                    "only in-progress work tasks can return to todo");
        }
        status = WorkTaskStatus.TODO;
        incrementProjectionVersion();
        incrementNotificationProjectionVersion();
        touch(now);
    }

    public void complete(LocalDateTime now) {
        ensureOpen();
        LocalDateTime actual = requiredTime(now, "work task completion time");
        status = WorkTaskStatus.DONE;
        completedTime = actual;
        cancelledTime = null;
        incrementProjectionVersion();
        incrementNotificationProjectionVersion();
        touch(actual);
    }

    public void cancel(LocalDateTime now) {
        ensureOpen();
        LocalDateTime actual = requiredTime(now, "work task cancellation time");
        status = WorkTaskStatus.CANCELLED;
        cancelledTime = actual;
        completedTime = null;
        incrementProjectionVersion();
        incrementNotificationProjectionVersion();
        touch(actual);
    }

    public void delete(boolean hasComments, boolean hasAttachmentHistory, LocalDateTime now) {
        if (status != WorkTaskStatus.TODO || firstStartedTime != null || hasComments
                || hasAttachmentHistory) {
            throw new WorkTaskDomainException(
                    "only never-started uncommented todo work tasks can be deleted");
        }
        status = WorkTaskStatus.DELETED;
        incrementProjectionVersion();
        incrementNotificationProjectionVersion();
        touch(now);
    }

    /** Backward-compatible domain call for callers compiled before B24a. */
    public void delete(boolean hasComments, LocalDateTime now) {
        delete(hasComments, false, now);
    }

    public boolean isCreatedBy(MemberId memberId) {
        return memberId != null && createdByMemberId.sameValueAs(memberId);
    }

    public boolean isAssignedTo(MemberId memberId) {
        return memberId != null && currentAssigneeMemberId.sameValueAs(memberId);
    }

    public boolean isOverdueAt(LocalDateTime now) {
        return status.isOpen() && dueTime != null
                && dueTime.isBefore(requiredTime(now, "work task overdue evaluation time"));
    }

    public void onPersisted(
            WorkTaskId id, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new WorkTaskDomainException("persisted work task ID is invalid");
        }
        this.id = id;
        restoreVersion(version);
        if (createdTime != null) {
            this.createdTime = truncate(createdTime);
        }
        if (updatedTime != null) {
            this.updatedTime = truncate(updatedTime);
        }
    }

    private void validateLifecycleTimes() {
        if (status == WorkTaskStatus.IN_PROGRESS && firstStartedTime == null) {
            throw new WorkTaskDomainException(
                    "in-progress work task first-start time is invalid");
        }
        if (status == WorkTaskStatus.DONE) {
            if (completedTime == null || cancelledTime != null) {
                throw new WorkTaskDomainException("completed work task times are invalid");
            }
        } else if (status == WorkTaskStatus.CANCELLED) {
            if (cancelledTime == null || completedTime != null) {
                throw new WorkTaskDomainException("cancelled work task times are invalid");
            }
        } else if (completedTime != null || cancelledTime != null) {
            throw new WorkTaskDomainException("open or deleted work task times are invalid");
        }
        if (status == WorkTaskStatus.DELETED && firstStartedTime != null) {
            throw new WorkTaskDomainException("deleted work task start time is invalid");
        }
    }

    private void ensureOpen() {
        if (!status.isOpen()) {
            throw new WorkTaskDomainException("finished work tasks are immutable");
        }
    }

    private void incrementProjectionVersion() {
        if (worklistProjectionVersion == Long.MAX_VALUE) {
            throw new WorkTaskDomainException(
                    "work task worklist projection version is exhausted");
        }
        worklistProjectionVersion++;
    }

    private void incrementNotificationProjectionVersion() {
        if (notificationProjectionVersion == Long.MAX_VALUE) {
            throw new WorkTaskDomainException(
                    "work task notification projection version is exhausted");
        }
        notificationProjectionVersion++;
    }

    private void touch(LocalDateTime now) {
        updatedTime = requiredTime(now, "work task update time");
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new WorkTaskDomainException("work task tenant is required");
        }
        return value;
    }

    private static MemberId requireMember(MemberId value, String description) {
        if (value == null) {
            throw new WorkTaskDomainException(description + " is required");
        }
        return value;
    }

    private static WorkTaskStatus requireStatus(WorkTaskStatus value) {
        if (value == null) {
            throw new WorkTaskDomainException("work task status is required");
        }
        return value;
    }

    private static Set<MemberId> followers(Collection<MemberId> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        if (values.size() > MAX_FOLLOWERS || values.stream().anyMatch(java.util.Objects::isNull)) {
            throw new WorkTaskDomainException("work task followers are invalid");
        }
        LinkedHashSet<MemberId> normalized = new LinkedHashSet<>(values);
        if (normalized.size() != values.size()) {
            throw new WorkTaskDomainException("work task followers must be unique");
        }
        return Set.copyOf(normalized);
    }

    private static String requiredText(String value, int max, String description) {
        String normalized = optionalText(value, max, description);
        if (normalized == null) {
            throw new WorkTaskDomainException(description + " is required");
        }
        return normalized;
    }

    private static String optionalText(String value, int max, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > max) {
            throw new WorkTaskDomainException(description + " is too long");
        }
        return normalized;
    }

    private static LocalDateTime optionalTime(LocalDateTime value, String description) {
        if (value == null) {
            return null;
        }
        if (value.getYear() < 1000 || value.getYear() > 9999) {
            throw new WorkTaskDomainException(description + " is outside the supported range");
        }
        return truncate(value);
    }

    private static LocalDateTime requiredTime(LocalDateTime value, String description) {
        LocalDateTime result = optionalTime(value, description);
        if (result == null) {
            throw new WorkTaskDomainException(description + " is required");
        }
        return result;
    }

    private static LocalDateTime truncate(LocalDateTime value) {
        return value.withNano(value.getNano() / 1_000 * 1_000);
    }
}
