package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.dto.response.ActivityRelationResponse;
import io.github.archetom.crm.api.dto.response.ActivityResponse;
import io.github.archetom.crm.application.vo.ActivityVO;

public final class ActivityResponseAssembler {
    private ActivityResponseAssembler() {
    }

    public static ActivityResponse toResponse(ActivityVO value) {
        return new ActivityResponse().setId(value.id()).setSubjectType(value.subjectType())
                .setSubjectId(value.subjectId()).setKind(value.kind())
                .setCreatorActorId(value.creatorActorId()).setCategory(value.category())
                .setPurpose(value.purpose()).setContent(value.content())
                .setOccurredTime(value.occurredTime()).setNextContactTime(value.nextContactTime())
                .setRelations(value.relations().stream().map(relation -> new ActivityRelationResponse()
                        .setObjectType(relation.objectType()).setRecordId(relation.recordId())).toList())
                .setVersion(value.version()).setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }
}
