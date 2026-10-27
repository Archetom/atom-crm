package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.model.ApprovalInstanceQuery;
import io.github.archetom.crm.domain.model.ApprovalInstanceReference;
import io.github.archetom.crm.domain.model.ApprovalInstanceSummary;
import io.github.archetom.crm.domain.model.ApprovalSubjectStatus;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApprovalInstanceRepository {
    ApprovalInstance save(TenantId tenantId, ApprovalInstance instance);

    Optional<ApprovalInstance> findById(
            TenantId tenantId, ApprovalInstanceId instanceId);

    Optional<ApprovalInstance> findByIdForUpdate(
            TenantId tenantId, ApprovalInstanceId instanceId);

    Optional<ApprovalInstance> findByIdempotencyKey(
            TenantId tenantId, String idempotencyKey);

    List<ApprovalInstanceSummary> findSummariesByIds(
            TenantId tenantId, Collection<ApprovalInstanceId> instanceIds);

    /** Hydrates only the already visibility-filtered final page for capability projection. */
    List<ApprovalInstance> findByIds(
            TenantId tenantId, Collection<ApprovalInstanceId> instanceIds);

    /**
     * Projects the latest approval state for business subjects that the caller has already
     * visibility-filtered. The projection intentionally omits workflow participants and facts.
     */
    List<ApprovalSubjectStatus> findLatestSubjectStatuses(
            TenantId tenantId, BusinessObjectType objectType, Collection<Long> subjectIds);

    PageResult<ApprovalInstanceReference> scanPage(
            TenantId tenantId, ApprovalInstanceQuery query);

}
