package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.StageWorkflowComment;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowCommentId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.StageWorkflowCommentPO;
import org.springframework.stereotype.Component;

@Component
public class StageWorkflowCommentPOConverter {

    public StageWorkflowCommentPO toPO(StageWorkflowComment comment) {
        if (comment == null) {
            return null;
        }
        StageWorkflowCommentPO po = new StageWorkflowCommentPO();
        po.setId(comment.getId() == null ? null : comment.getId().getValue());
        po.setTenantId(comment.getTenantId().getValue());
        po.setWorkflowRecordId(comment.getWorkflowRecordId().getValue());
        po.setAuthorActorId(comment.getAuthorMemberId().getValue());
        po.setContent(comment.getContent());
        po.setStatus(comment.isDeleted() ? "DELETED" : "ACTIVE");
        po.setVersion(comment.getVersion());
        po.setCreatedTime(comment.getCreatedTime());
        po.setUpdatedTime(comment.getUpdatedTime());
        return po;
    }

    public StageWorkflowComment toDomain(StageWorkflowCommentPO po) {
        if (po == null) {
            return null;
        }
        return StageWorkflowComment.reconstitute(new StageWorkflowComment.Snapshot(
                new StageWorkflowCommentId(po.getId()), new TenantId(po.getTenantId()),
                new StageWorkflowRecordId(po.getWorkflowRecordId()),
                new MemberId(po.getAuthorActorId()), po.getContent(),
                "DELETED".equals(po.getStatus()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime()));
    }
}
