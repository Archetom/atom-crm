package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.model.WorkTaskPriority;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.WorkTaskBusinessRecord;
import io.github.archetom.crm.domain.model.WorkTaskOrigin;
import io.github.archetom.crm.domain.model.WorkTaskStatus;
import io.github.archetom.crm.domain.model.WorkTaskReminderPlan;
import io.github.archetom.crm.domain.model.WorkTaskReminderStep;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskPO;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class WorkTaskPOConverter {

    public WorkTaskPO toPO(WorkTask task) {
        WorkTaskPO po = new WorkTaskPO();
        po.setId(task.getId() == null ? null : task.getId().getValue());
        po.setTenantId(task.getTenantId().getValue());
        po.setCreatedByActorId(task.getCreatedByMemberId().getValue());
        po.setCurrentAssigneeActorId(task.getCurrentAssigneeMemberId().getValue());
        WorkTaskOrigin origin = task.getOrigin() == null ? WorkTaskOrigin.manual() : task.getOrigin();
        po.setOriginKind(origin.kind().name());
        po.setOriginSeriesId(origin.seriesId());
        po.setOriginSeriesGeneration(origin.seriesGeneration());
        po.setOriginOccurrenceSequence(origin.occurrenceSequence());
        po.setOriginNominalLocalTime(origin.nominalLocalTime());
        po.setOriginZoneId(origin.zoneId());
        po.setOriginOffsetSeconds(origin.resolvedOffsetSeconds());
        po.setOriginResolvedDueTime(origin.resolvedDueTime());
        po.setBusinessObjectType(task.getBusinessRecord() == null ? null
                : task.getBusinessRecord().objectType().name());
        po.setBusinessRecordId(task.getBusinessRecord() == null ? null
                : task.getBusinessRecord().recordId());
        po.setTitle(task.getTitle());
        po.setDescription(task.getDescription());
        po.setPriority(task.getPriority().name());
        po.setDueTime(task.getDueTime());
        po.setStatus(task.getStatus().name());
        po.setFirstStartedTime(task.getFirstStartedTime());
        po.setWorklistProjectionVersion(task.getWorklistProjectionVersion());
        po.setNotificationProjectionVersion(task.getNotificationProjectionVersion());
        po.setCompletedTime(task.getCompletedTime());
        po.setCancelledTime(task.getCancelledTime());
        po.setVersion(task.getVersion());
        po.setCreatedTime(task.getCreatedTime());
        po.setUpdatedTime(task.getUpdatedTime());
        return po;
    }

    public WorkTask toDomain(WorkTaskPO po, Collection<Long> followerActorIds) {
        return toDomain(po, followerActorIds, java.util.List.of());
    }

    public WorkTask toDomain(WorkTaskPO po, Collection<Long> followerActorIds,
                             Collection<WorkTaskReminderStep> reminderSteps) {
        if (po == null) {
            return null;
        }
        return WorkTask.reconstitute(new WorkTask.Snapshot(
                new WorkTaskId(po.getId()),
                new TenantId(po.getTenantId()),
                new MemberId(po.getCreatedByActorId()),
                new MemberId(po.getCurrentAssigneeActorId()),
                followerActorIds == null ? Set.of() : followerActorIds.stream()
                        .map(MemberId::new).collect(Collectors.toUnmodifiableSet()),
                po.getBusinessObjectType() == null ? null : new WorkTaskBusinessRecord(
                        BusinessObjectType.valueOf(po.getBusinessObjectType()),
                        po.getBusinessRecordId()),
                po.getTitle(),
                po.getDescription(),
                WorkTaskPriority.valueOf(po.getPriority()),
                po.getDueTime(),
                WorkTaskStatus.valueOf(po.getStatus()),
                po.getFirstStartedTime(),
                po.getWorklistProjectionVersion(),
                po.getNotificationProjectionVersion() == null ? 0L : po.getNotificationProjectionVersion(),
                WorkTaskReminderPlan.of(reminderSteps),
                po.getCompletedTime(),
                po.getCancelledTime(),
                po.getVersion(),
                po.getCreatedTime(),
                po.getUpdatedTime(),
                origin(po)));
    }

    private WorkTaskOrigin origin(WorkTaskPO po) {
        if (po.getOriginKind() == null || WorkTaskOrigin.Kind.MANUAL.name().equals(po.getOriginKind())) {
            return WorkTaskOrigin.manual();
        }
        return WorkTaskOrigin.recurrence(po.getOriginSeriesId(), po.getOriginSeriesGeneration(),
                po.getOriginOccurrenceSequence(), po.getOriginNominalLocalTime(), po.getOriginZoneId(),
                po.getOriginOffsetSeconds(), po.getOriginResolvedDueTime());
    }
}
