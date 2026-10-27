package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.model.WorkTaskQuery;
import io.github.archetom.crm.domain.model.WorkTaskWorklistRecord;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkTaskRepository {

    WorkTask save(TenantId tenantId, WorkTask task);

    Optional<WorkTask> findVisibleById(
            TenantId tenantId, WorkTaskId taskId,
            MemberId viewerMemberId, boolean taskManager);

    Optional<WorkTask> findVisibleByIdForUpdate(
            TenantId tenantId, WorkTaskId taskId,
            MemberId viewerMemberId, boolean taskManager);

    /** Internal B24 worker lock after a durable typed relation has already proven ownership. */
    Optional<WorkTask> findByIdForAttachmentMutation(TenantId tenantId, WorkTaskId taskId);

    /** Internal B27 worker lock after an exact reminder occurrence has supplied the tenant. */
    Optional<WorkTask> findByIdForReminderEffect(TenantId tenantId, WorkTaskId taskId);

    PageResult<WorkTask> findPage(TenantId tenantId, WorkTaskQuery query);

    List<WorkTaskWorklistRecord> findDueForAssignee(
            TenantId tenantId, MemberId assigneeMemberId,
            LocalDateTime fromInclusive, LocalDateTime untilExclusive,
            LocalDateTime afterTime, Long afterId, int limit);
}
