package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.application.vo.WorkTaskVO;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.model.WorkTaskPriority;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.WorkTaskBusinessRecord;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.WorkTaskCommentId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class WorkTaskApplicationSupport {

    WorkTaskId taskId(Long value) {
        try {
            return new WorkTaskId(value);
        } catch (RuntimeException exception) {
            throw invalid("Work task ID is invalid");
        }
    }

    MemberId memberId(Long value) {
        try {
            return new MemberId(value);
        } catch (RuntimeException exception) {
            throw invalid("Work task assignee is invalid");
        }
    }

    WorkTaskCommentId commentId(Long value) {
        try {
            return new WorkTaskCommentId(value);
        } catch (RuntimeException exception) {
            throw invalid("Work task comment ID is invalid");
        }
    }

    List<MemberId> followerIds(Collection<Long> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > WorkTask.MAX_FOLLOWERS) {
            throw invalid("Work task followers are invalid");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>(values);
        if (unique.size() != values.size()
                || unique.stream().anyMatch(value -> value == null || value <= 0)) {
            throw invalid("Work task followers must be unique active tenant members");
        }
        return unique.stream().map(MemberId::new).toList();
    }

    WorkTaskBusinessRecord businessRecord(String objectType, Long recordId) {
        boolean typeMissing = objectType == null || objectType.isBlank();
        if (typeMissing && recordId == null) {
            return null;
        }
        if (typeMissing || recordId == null || recordId <= 0) {
            throw invalid("Work task business record is invalid");
        }
        try {
            BusinessObjectType type = BusinessObjectType.valueOf(
                    objectType.trim().toUpperCase(Locale.ROOT));
            if (!WorkTaskBusinessRecord.supports(type)) {
                throw new IllegalArgumentException();
            }
            return new WorkTaskBusinessRecord(type, recordId);
        } catch (IllegalArgumentException exception) {
            throw invalid("Work task business record type is invalid");
        }
    }

    WorkTaskPriority priority(String value, boolean defaultNormal) {
        if (defaultNormal && (value == null || value.isBlank())) {
            return WorkTaskPriority.NORMAL;
        }
        try {
            return WorkTaskPriority.valueOf(value == null ? ""
                    : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("Work task priority is invalid");
        }
    }

    void requireVersion(WorkTask task, Long version) {
        if (task == null || version == null || version < 0
                || !version.equals(task.getVersion())) {
            throw new AggregateVersionConflictException();
        }
    }

    WorkTaskVO toVO(WorkTask task, LocalDateTime now, boolean businessRecordVisible) {
        WorkTaskBusinessRecord businessRecord = businessRecordVisible
                ? task.getBusinessRecord() : null;
        return new WorkTaskVO(
                task.getId().getValue(),
                task.getCreatedByMemberId().getValue(),
                task.getCurrentAssigneeMemberId().getValue(),
                task.getFollowerMemberIds().stream().map(MemberId::getValue)
                        .sorted().toList(),
                task.getOrigin().kind().name(),
                task.getOrigin().seriesId(),
                task.getOrigin().seriesGeneration(),
                task.getOrigin().occurrenceSequence(),
                task.getOrigin().nominalLocalTime(),
                task.getOrigin().zoneId(),
                task.getOrigin().resolvedOffsetSeconds(),
                task.getOrigin().resolvedDueTime(),
                businessRecord == null ? null : businessRecord.objectType().name(),
                businessRecord == null ? null : businessRecord.recordId(),
                task.getTitle(),
                task.getDescription(),
                task.getPriority().name(),
                task.getDueTime(),
                task.isOverdueAt(now),
                task.getStatus().name(),
                task.getFirstStartedTime(),
                task.getWorklistProjectionVersion(),
                task.getCompletedTime(),
                task.getCancelledTime(),
                task.getVersion(),
                task.getCreatedTime(),
                task.getUpdatedTime());
    }

    io.github.archetom.crm.application.vo.WorkTaskCommentVO toVO(
            io.github.archetom.crm.domain.entity.WorkTaskComment comment) {
        return new io.github.archetom.crm.application.vo.WorkTaskCommentVO(
                comment.getId().getValue(),
                comment.getWorkTaskId().getValue(),
                comment.getAuthorMemberId().getValue(),
                comment.getContent(),
                comment.getVersion(),
                comment.getCreatedTime(),
                comment.getUpdatedTime());
    }

    NonRetryableApplicationException invalid(String message) {
        return error(ApplicationErrorCode.PARAMETER_INVALID, message);
    }

    NonRetryableApplicationException denied(String message) {
        return error(ApplicationErrorCode.ACCESS_DENIED, message);
    }

    NonRetryableApplicationException notFound(String message) {
        return error(ApplicationErrorCode.RESOURCE_NOT_FOUND, message);
    }

    private NonRetryableApplicationException error(
            ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }
}
