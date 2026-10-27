package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.WorkTaskDomainException;
import io.github.archetom.crm.domain.model.NotificationKind;
import io.github.archetom.crm.domain.model.NotificationStatus;
import io.github.archetom.crm.domain.model.WorkTaskPriority;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.NotificationId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import io.github.archetom.crm.domain.valueobject.WorkTaskReminderOccurrenceId;
import lombok.Getter;

import java.time.LocalDateTime;

/** Self-only, Work-origin in-app inbox fact. It never represents external delivery. */
@Getter
public class Notification extends AggregateRoot<NotificationId> {
    private NotificationId id;
    private TenantId tenantId;
    private MemberId recipientMemberId;
    private NotificationKind kind;
    private WorkTaskReminderOccurrenceId reminderOccurrenceId;
    private WorkTaskId taskId;
    private long taskNotificationProjectionVersion;
    private String title;
    private WorkTaskPriority priority;
    private LocalDateTime dueTime;
    private NotificationStatus status;
    private LocalDateTime readTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private Notification() {
    }

    public record Snapshot(
            NotificationId id, TenantId tenantId, MemberId recipientMemberId,
            NotificationKind kind, WorkTaskReminderOccurrenceId reminderOccurrenceId,
            WorkTaskId taskId, long taskNotificationProjectionVersion, String title,
            WorkTaskPriority priority, LocalDateTime dueTime, NotificationStatus status,
            LocalDateTime readTime, LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public static Notification create(
            TenantId tenantId, MemberId recipientMemberId, NotificationKind kind,
            WorkTaskReminderOccurrenceId reminderOccurrenceId, WorkTaskId taskId,
            long taskNotificationProjectionVersion, String title, WorkTaskPriority priority,
            LocalDateTime dueTime, LocalDateTime now) {
        return reconstitute(new Snapshot(null, tenantId, recipientMemberId, kind,
                reminderOccurrenceId, taskId, taskNotificationProjectionVersion, title, priority,
                dueTime, NotificationStatus.UNREAD, null, now, now));
    }

    public static Notification reconstitute(Snapshot snapshot) {
        if (snapshot == null || snapshot.tenantId() == null || snapshot.recipientMemberId() == null
                || snapshot.kind() == null || snapshot.reminderOccurrenceId() == null
                || snapshot.taskId() == null || snapshot.taskNotificationProjectionVersion() < 0
                || snapshot.title() == null || snapshot.title().isBlank()
                || snapshot.title().codePointCount(0, snapshot.title().length()) > WorkTask.MAX_TITLE_CODE_POINTS
                || snapshot.priority() == null || snapshot.dueTime() == null || snapshot.status() == null
                || snapshot.createdTime() == null || snapshot.updatedTime() == null) {
            throw new WorkTaskDomainException("notification snapshot is invalid");
        }
        if ((snapshot.status() == NotificationStatus.READ) != (snapshot.readTime() != null)
                || snapshot.status() == NotificationStatus.WITHDRAWN && snapshot.readTime() != null) {
            throw new WorkTaskDomainException("notification read state is invalid");
        }
        Notification value = new Notification();
        value.id = snapshot.id();
        value.tenantId = snapshot.tenantId();
        value.recipientMemberId = snapshot.recipientMemberId();
        value.kind = snapshot.kind();
        value.reminderOccurrenceId = snapshot.reminderOccurrenceId();
        value.taskId = snapshot.taskId();
        value.taskNotificationProjectionVersion = snapshot.taskNotificationProjectionVersion();
        value.title = snapshot.title().strip();
        value.priority = snapshot.priority();
        value.dueTime = truncate(snapshot.dueTime());
        value.status = snapshot.status();
        value.readTime = snapshot.readTime() == null ? null : truncate(snapshot.readTime());
        value.createdTime = truncate(snapshot.createdTime());
        value.updatedTime = truncate(snapshot.updatedTime());
        return value;
    }

    public void onPersisted(NotificationId notificationId, LocalDateTime created, LocalDateTime updated) {
        if (notificationId == null || id != null && !id.sameValueAs(notificationId)) {
            throw new WorkTaskDomainException("persisted notification ID is invalid");
        }
        id = notificationId;
        if (created != null) createdTime = truncate(created);
        if (updated != null) updatedTime = truncate(updated);
    }

    private static LocalDateTime truncate(LocalDateTime value) {
        if (value.getYear() < 1000 || value.getYear() > 9999) {
            throw new WorkTaskDomainException("notification time is invalid");
        }
        return value.withNano(value.getNano() / 1_000 * 1_000);
    }
}
