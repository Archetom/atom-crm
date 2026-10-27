package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.valueobject.LeadId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.Set;

/** Personal lead-star relationship inside a verified tenant membership. */
public interface LeadStarRepository {

    void setStarred(TenantId tenantId, MemberId memberId, LeadId leadId, boolean starred);

    boolean isStarred(TenantId tenantId, MemberId memberId, LeadId leadId);

    Set<LeadId> findStarred(
            TenantId tenantId, MemberId memberId, Collection<LeadId> leadIds);
}
