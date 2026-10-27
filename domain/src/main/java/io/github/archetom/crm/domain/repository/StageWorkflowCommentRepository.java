package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.StageWorkflowComment;
import io.github.archetom.crm.domain.valueobject.StageWorkflowCommentId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Optional;

public interface StageWorkflowCommentRepository {
    StageWorkflowComment save(TenantId tenantId, StageWorkflowComment comment);

    Optional<StageWorkflowComment> findById(
            TenantId tenantId, StageWorkflowRecordId recordId,
            StageWorkflowCommentId commentId);

    PageResult<StageWorkflowComment> findPage(
            TenantId tenantId, StageWorkflowRecordId recordId, int page, int size);
}
