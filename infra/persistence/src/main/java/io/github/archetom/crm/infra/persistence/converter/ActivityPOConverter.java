package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.Activity;
import io.github.archetom.crm.domain.entity.ActivityRelation;
import io.github.archetom.crm.domain.model.ActivityKind;
import io.github.archetom.crm.domain.model.ActivityStatus;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.ActivityId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ActivityPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ActivityRelationPO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ActivityPOConverter {
    public ActivityPO toPO(Activity value) {
        ActivityPO po = new ActivityPO();
        po.setId(value.getId() == null ? null : value.getId().getValue());
        po.setTenantId(value.getTenantId().getValue());
        po.setSubjectType(value.getSubjectType().name());
        po.setSubjectId(value.getSubjectId());
        po.setKind(value.getKind().name());
        po.setCreatorActorId(value.getCreatorMemberId().getValue());
        po.setCategory(value.getCategory());
        po.setPurpose(value.getPurpose());
        po.setContent(value.getContent());
        po.setOccurredTime(value.getOccurredTime());
        po.setNextContactTime(value.getNextContactTime());
        po.setStatus(value.getStatus().name());
        po.setVersion(value.getVersion());
        po.setCreatedTime(value.getCreatedTime());
        po.setUpdatedTime(value.getUpdatedTime());
        return po;
    }

    public Activity toDomain(ActivityPO root, List<ActivityRelationPO> relations) {
        return Activity.reconstitute(new Activity.Snapshot(new ActivityId(root.getId()),
                new TenantId(root.getTenantId()), BusinessObjectType.valueOf(root.getSubjectType()),
                root.getSubjectId(), ActivityKind.valueOf(root.getKind()),
                new MemberId(root.getCreatorActorId()), root.getCategory(), root.getPurpose(),
                root.getContent(), root.getOccurredTime(), root.getNextContactTime(),
                relations.stream().map(value -> new ActivityRelation(
                        BusinessObjectType.valueOf(value.getObjectType()), value.getRecordId())).toList(),
                ActivityStatus.valueOf(root.getStatus()), root.getVersion(), root.getCreatedTime(),
                root.getUpdatedTime()));
    }

    public ActivityRelationPO toPO(TenantId tenantId, ActivityId activityId, ActivityRelation relation) {
        ActivityRelationPO po = new ActivityRelationPO();
        po.setTenantId(tenantId.getValue());
        po.setActivityId(activityId.getValue());
        po.setObjectType(relation.objectType().name());
        po.setRecordId(relation.recordId());
        po.setCreatedTime(LocalDateTime.now());
        return po;
    }
}
