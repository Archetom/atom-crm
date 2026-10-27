package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.WorkTaskComment;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskCommentId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;

import java.util.Optional;

public interface WorkTaskCommentRepository {

    WorkTaskComment save(TenantId tenantId, WorkTaskComment comment);

    Optional<WorkTaskComment> findByIdForUpdate(
            TenantId tenantId, WorkTaskId taskId, WorkTaskCommentId commentId);

    PageResult<WorkTaskComment> findPage(
            TenantId tenantId, WorkTaskId taskId, int page, int size);

    /** Includes soft-deleted history so a commented task can never be mistaken for a draft. */
    boolean existsAny(TenantId tenantId, WorkTaskId taskId);
}
