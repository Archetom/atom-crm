package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.OaRequest;
import io.github.archetom.crm.domain.model.OaRequestQuery;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.OaFormId;
import io.github.archetom.crm.domain.valueobject.OaRequestId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface OaRequestRepository {

    OaRequest save(
            TenantId tenantId, OaRequest request,
            Collection<FieldDefinition> fieldDefinitions);

    Optional<OaRequest> findById(TenantId tenantId, OaRequestId requestId);

    /** Used only after the corresponding approval instance has been locked. */
    Optional<OaRequest> findByIdForUpdate(TenantId tenantId, OaRequestId requestId);

    /** Locks the OA root after its exact approval instance, preserving B29d's lock order. */
    Optional<OaRequest> findByApprovalInstanceForUpdate(
            TenantId tenantId, ApprovalInstanceId approvalInstanceId);

    Optional<OaRequest> findByIdempotencyKey(
            TenantId tenantId, MemberId applicantMemberId, String idempotencyKey);

    Optional<OaRequest> findVisibleById(
            TenantId tenantId, OaRequestId requestId,
            MemberId memberId, boolean tenantManager);

    Set<Long> findVisibleIds(
            TenantId tenantId, Collection<OaRequestId> requestIds,
            MemberId memberId, boolean tenantManager);

    PageResult<OaRequest> findVisiblePage(TenantId tenantId, OaRequestQuery query);

    boolean existsByForm(TenantId tenantId, OaFormId formId);
}
