package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.WorkTaskComment;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskCommentId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskCommentPO;
import org.springframework.stereotype.Component;

@Component
public class WorkTaskCommentPOConverter {

    public WorkTaskCommentPO toPO(WorkTaskComment comment) {
        WorkTaskCommentPO po = new WorkTaskCommentPO();
        po.setId(comment.getId() == null ? null : comment.getId().getValue());
        po.setTenantId(comment.getTenantId().getValue());
        po.setWorkTaskId(comment.getWorkTaskId().getValue());
        po.setAuthorActorId(comment.getAuthorMemberId().getValue());
        po.setContent(comment.getContent());
        po.setStatus(comment.isDeleted() ? "DELETED" : "ACTIVE");
        po.setVersion(comment.getVersion());
        po.setCreatedTime(comment.getCreatedTime());
        po.setUpdatedTime(comment.getUpdatedTime());
        return po;
    }

    public WorkTaskComment toDomain(WorkTaskCommentPO po) {
        if (po == null) {
            return null;
        }
        return WorkTaskComment.reconstitute(new WorkTaskComment.Snapshot(
                new WorkTaskCommentId(po.getId()),
                new TenantId(po.getTenantId()),
                new WorkTaskId(po.getWorkTaskId()),
                new MemberId(po.getAuthorActorId()),
                po.getContent(),
                "DELETED".equals(po.getStatus()),
                po.getVersion(),
                po.getCreatedTime(),
                po.getUpdatedTime()));
    }
}
