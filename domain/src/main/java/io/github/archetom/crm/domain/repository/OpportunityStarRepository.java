package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.Set;

/** Personal opportunity-star relationship inside a verified tenant membership. */
public interface OpportunityStarRepository {

    void setStarred(
            TenantId tenantId, MemberId memberId,
            OpportunityId opportunityId, boolean starred);

    boolean isStarred(
            TenantId tenantId, MemberId memberId, OpportunityId opportunityId);

    Set<OpportunityId> findStarred(
            TenantId tenantId, MemberId memberId,
            Collection<OpportunityId> opportunityIds);
}
