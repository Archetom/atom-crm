package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.WorkTaskDomainException;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskCommentId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import lombok.Getter;

import java.time.LocalDateTime;

/** Independently paged, author-deletable plain-text comment. */
@Getter
public class WorkTaskComment extends AggregateRoot<WorkTaskCommentId> {

    public static final int MAX_CONTENT_CODE_POINTS = 5_000;

    private WorkTaskCommentId id;
    private TenantId tenantId;
    private WorkTaskId workTaskId;
    private MemberId authorMemberId;
    private String content;
    private boolean deleted;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private WorkTaskComment() {
    }

    public record Snapshot(
            WorkTaskCommentId id,
            TenantId tenantId,
            WorkTaskId workTaskId,
            MemberId authorMemberId,
            String content,
            boolean deleted,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime
    ) {
    }

    public static WorkTaskComment create(
            TenantId tenantId, WorkTaskId taskId, MemberId author,
            String content, LocalDateTime now) {
        WorkTaskComment comment = new WorkTaskComment();
        comment.tenantId = require(tenantId, "work task comment tenant");
        comment.workTaskId = require(taskId, "work task comment task");
        comment.authorMemberId = require(author, "work task comment author");
        comment.content = content(content);
        comment.createdTime = time(now, "work task comment creation time");
        comment.updatedTime = comment.createdTime;
        return comment;
    }

    public static WorkTaskComment reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new WorkTaskDomainException("work task comment snapshot is required");
        }
        WorkTaskComment comment = new WorkTaskComment();
        comment.id = snapshot.id();
        comment.tenantId = require(snapshot.tenantId(), "work task comment tenant");
        comment.workTaskId = require(snapshot.workTaskId(), "work task comment task");
        comment.authorMemberId = require(snapshot.authorMemberId(), "work task comment author");
        comment.content = content(snapshot.content());
        comment.deleted = snapshot.deleted();
        comment.restoreVersion(snapshot.version());
        comment.createdTime = time(snapshot.createdTime(), "work task comment creation time");
        comment.updatedTime = time(snapshot.updatedTime(), "work task comment update time");
        return comment;
    }

    public void delete(MemberId author, LocalDateTime now) {
        if (deleted || author == null || !authorMemberId.sameValueAs(author)) {
            throw new WorkTaskDomainException(
                    "only the author can delete an active work task comment");
        }
        deleted = true;
        updatedTime = time(now, "work task comment deletion time");
    }

    public void onPersisted(
            WorkTaskCommentId persistedId, Long persistedVersion,
            LocalDateTime created, LocalDateTime updated) {
        if (persistedId == null || id != null && !id.sameValueAs(persistedId)) {
            throw new WorkTaskDomainException("persisted work task comment ID is invalid");
        }
        id = persistedId;
        restoreVersion(persistedVersion);
        if (created != null) {
            createdTime = truncate(created);
        }
        if (updated != null) {
            updatedTime = truncate(updated);
        }
    }

    private static String content(String value) {
        String normalized = value == null ? null : value.strip();
        if (normalized == null || normalized.isBlank()
                || normalized.codePointCount(0, normalized.length())
                > MAX_CONTENT_CODE_POINTS) {
            throw new WorkTaskDomainException("work task comment content is invalid");
        }
        return normalized;
    }

    private static LocalDateTime time(LocalDateTime value, String description) {
        if (value == null || value.getYear() < 1000 || value.getYear() > 9999) {
            throw new WorkTaskDomainException(description + " is invalid");
        }
        return truncate(value);
    }

    private static LocalDateTime truncate(LocalDateTime value) {
        return value.withNano(value.getNano() / 1_000 * 1_000);
    }

    private static <T> T require(T value, String description) {
        if (value == null) {
            throw new WorkTaskDomainException(description + " is required");
        }
        return value;
    }
}
