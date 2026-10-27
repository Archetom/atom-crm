package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.MarketingActivity;
import io.github.archetom.crm.domain.model.MarketingActivityRelatedObjectType;
import io.github.archetom.crm.domain.model.MarketingActivityStatus;
import io.github.archetom.crm.domain.valueobject.MarketingActivityId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.MarketingActivityPO;
import io.github.archetom.crm.infra.persistence.mysql.po.MarketingActivityParticipantPO;
import io.github.archetom.crm.infra.persistence.mysql.po.MarketingActivityParticipantDepartmentPO;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class MarketingActivityPOConverter {
    public MarketingActivity toDomain(
            MarketingActivityPO root, Collection<MarketingActivityParticipantPO> participantPOs,
            Collection<MarketingActivityParticipantDepartmentPO> participantDepartmentPOs) {
        if (root == null) return null;
        List<MemberId> participants = participantPOs == null ? List.of() : participantPOs.stream()
                .map(value -> new MemberId(value.getActorId())).toList();
        List<DepartmentId> departments = participantDepartmentPOs == null ? List.of()
                : participantDepartmentPOs.stream().map(value -> new DepartmentId(value.getDepartmentId()))
                        .toList();
        return MarketingActivity.reconstitute(new MarketingActivity.Snapshot(
                new MarketingActivityId(root.getId()), new TenantId(root.getTenantId()),
                new MemberId(root.getCreatedByActorId()), root.getName(),
                MarketingActivityRelatedObjectType.valueOf(root.getRelatedObjectType()),
                root.getMarketingFormId() == null ? null : new MarketingFormId(root.getMarketingFormId()),
                participants, departments,
                root.getActivityType(), root.getStartTime(), root.getEndTime(), root.getBudget(),
                root.getAddress(), root.getDescription(), root.getViewCount(), root.getSubmissionCount(),
                Boolean.TRUE.equals(root.getOneSubmissionPerUser()),
                MarketingActivityStatus.valueOf(root.getStatus()),
                root.getVersion(), root.getCreatedTime(),
                root.getUpdatedTime()));
    }

    public MarketingActivityPO toPO(MarketingActivity activity) {
        if (activity == null) return null;
        MarketingActivityPO po = new MarketingActivityPO();
        po.setId(activity.getId() == null ? null : activity.getId().getValue());
        po.setTenantId(activity.getTenantId().getValue());
        po.setCreatedByActorId(activity.getCreatedByMemberId().getValue());
        po.setName(activity.getName());
        po.setRelatedObjectType(activity.getRelatedObjectType().name());
        po.setMarketingFormId(activity.getMarketingFormId() == null
                ? null : activity.getMarketingFormId().getValue());
        po.setActivityType(activity.getActivityType());
        po.setStartTime(activity.getStartTime());
        po.setEndTime(activity.getEndTime());
        po.setBudget(activity.getBudget());
        po.setAddress(activity.getAddress());
        po.setDescription(activity.getDescription());
        po.setViewCount(activity.getViewCount());
        po.setSubmissionCount(activity.getSubmissionCount());
        po.setOneSubmissionPerUser(activity.isOneSubmissionPerUser());
        po.setStatus(activity.getStatus().name());
        po.setVersion(activity.getVersion());
        po.setCreatedTime(activity.getCreatedTime());
        po.setUpdatedTime(activity.getUpdatedTime());
        return po;
    }

    public MarketingActivityParticipantPO participant(TenantId tenantId,
                                                       MarketingActivityId activityId,
                                                       MemberId participantId) {
        MarketingActivityParticipantPO po = new MarketingActivityParticipantPO();
        po.setTenantId(tenantId.getValue());
        po.setMarketingActivityId(activityId.getValue());
        po.setActorId(participantId.getValue());
        return po;
    }

    public MarketingActivityParticipantDepartmentPO participantDepartment(
            TenantId tenantId, MarketingActivityId activityId, DepartmentId departmentId) {
        MarketingActivityParticipantDepartmentPO po = new MarketingActivityParticipantDepartmentPO();
        po.setTenantId(tenantId.getValue());
        po.setMarketingActivityId(activityId.getValue());
        po.setDepartmentId(departmentId.getValue());
        return po;
    }
}
