package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.StageWorkflowDomainException;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowCommentId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class StageWorkflowComment extends AggregateRoot<StageWorkflowCommentId> {

    private StageWorkflowCommentId id;
    private TenantId tenantId;
    private StageWorkflowRecordId workflowRecordId;
    private MemberId authorMemberId;
    private String content;
    private boolean deleted;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private StageWorkflowComment() {
    }

    public record Snapshot(
            StageWorkflowCommentId id,
            TenantId tenantId,
            StageWorkflowRecordId workflowRecordId,
            MemberId authorMemberId,
            String content,
            boolean deleted,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static StageWorkflowComment create(TenantId tenantId,
                                              StageWorkflowRecordId recordId,
                                              MemberId author, String content) {
        StageWorkflowComment comment = new StageWorkflowComment();
        comment.tenantId = require(tenantId, "comment tenant");
        comment.workflowRecordId = require(recordId, "workflow record");
        comment.authorMemberId = require(author, "comment author");
        comment.content = content(content);
        LocalDateTime now = LocalDateTime.now();
        comment.createdTime = now;
        comment.updatedTime = now;
        return comment;
    }

    public static StageWorkflowComment reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new StageWorkflowDomainException("stage comment snapshot must not be empty");
        }
        StageWorkflowComment comment = new StageWorkflowComment();
        comment.id = snapshot.id();
        comment.tenantId = require(snapshot.tenantId(), "comment tenant");
        comment.workflowRecordId = require(snapshot.workflowRecordId(), "workflow record");
        comment.authorMemberId = require(snapshot.authorMemberId(), "comment author");
        comment.content = content(snapshot.content());
        comment.deleted = snapshot.deleted();
        comment.restoreVersion(snapshot.version());
        comment.createdTime = snapshot.createdTime();
        comment.updatedTime = snapshot.updatedTime();
        return comment;
    }

    public void delete(MemberId actor) {
        if (deleted || !authorMemberId.equals(actor)) {
            throw new StageWorkflowDomainException("only the author can delete an active comment");
        }
        deleted = true;
        updatedTime = LocalDateTime.now();
    }

    public void onPersisted(StageWorkflowCommentId persistedId, Long persistedVersion,
                            LocalDateTime created, LocalDateTime updated) {
        if (persistedId == null || id != null && !id.sameValueAs(persistedId)) {
            throw new StageWorkflowDomainException("persisted stage comment ID is invalid");
        }
        id = persistedId;
        restoreVersion(persistedVersion);
        if (created != null) {
            createdTime = created;
        }
        if (updated != null) {
            updatedTime = updated;
        }
    }

    private static String content(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > 2000) {
            throw new StageWorkflowDomainException("stage comment content is invalid");
        }
        return normalized;
    }

    private static <T> T require(T value, String description) {
        if (value == null) {
            throw new StageWorkflowDomainException(description + " is required");
        }
        return value;
    }
}
