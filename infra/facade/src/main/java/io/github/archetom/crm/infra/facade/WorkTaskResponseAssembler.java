package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.dto.response.WorkTaskResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskCommentResponse;
import io.github.archetom.crm.application.vo.WorkTaskCommentVO;
import io.github.archetom.crm.application.vo.WorkTaskVO;

final class WorkTaskResponseAssembler {

    private WorkTaskResponseAssembler() {
    }

    static WorkTaskResponse toResponse(WorkTaskVO value) {
        return new WorkTaskResponse()
                .setId(value.id())
                .setCreatedByActorId(value.createdByActorId())
                .setCurrentAssigneeActorId(value.currentAssigneeActorId())
                .setFollowerActorIds(value.followerActorIds())
                .setOriginKind(value.originKind())
                .setOriginSeriesId(value.originSeriesId())
                .setOriginSeriesGeneration(value.originSeriesGeneration())
                .setOriginOccurrenceSequence(value.originOccurrenceSequence())
                .setOriginNominalLocalTime(value.originNominalLocalTime())
                .setOriginZoneId(value.originZoneId())
                .setOriginOffsetSeconds(value.originOffsetSeconds())
                .setOriginResolvedDueTime(value.originResolvedDueTime())
                .setBusinessObjectType(value.businessObjectType())
                .setBusinessRecordId(value.businessRecordId())
                .setTitle(value.title())
                .setDescription(value.description())
                .setPriority(value.priority())
                .setDueTime(value.dueTime())
                .setOverdue(value.overdue())
                .setStatus(value.status())
                .setFirstStartedTime(value.firstStartedTime())
                .setWorklistProjectionVersion(value.worklistProjectionVersion())
                .setCompletedTime(value.completedTime())
                .setCancelledTime(value.cancelledTime())
                .setVersion(value.version())
                .setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }

    static WorkTaskCommentResponse toResponse(WorkTaskCommentVO value) {
        return new WorkTaskCommentResponse()
                .setId(value.id())
                .setTaskId(value.taskId())
                .setAuthorActorId(value.authorActorId())
                .setContent(value.content())
                .setVersion(value.version())
                .setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }
}
