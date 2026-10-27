package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.MembershipRequest;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.MembershipRequestId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.repository.PageResult;

import java.util.Optional;

/** Persistence port for auditable requests to join a tenant. */
public interface MembershipRequestRepository {

    MembershipRequest save(MembershipRequest request);

    Optional<MembershipRequest> findByIdForTarget(TenantId targetTenantId, MembershipRequestId requestId);

    Optional<MembershipRequest> findByIdForApplicant(MemberId applicantId, MembershipRequestId requestId);

    boolean hasPending(MemberId applicantId, TenantId targetTenantId);

    PageResult<MembershipRequest> findByTargetTenant(TenantId tenantId, int page, int size);

    PageResult<MembershipRequest> findByApplicant(MemberId applicantId, int page, int size);
}
