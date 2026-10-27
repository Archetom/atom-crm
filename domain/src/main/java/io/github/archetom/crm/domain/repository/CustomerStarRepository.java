package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.Set;

/** Personal customer-star relationship inside a verified tenant membership. */
public interface CustomerStarRepository {

    void setStarred(TenantId tenantId, MemberId memberId, CustomerId customerId, boolean starred);

    boolean isStarred(TenantId tenantId, MemberId memberId, CustomerId customerId);

    Set<CustomerId> findStarred(
            TenantId tenantId, MemberId memberId, Collection<CustomerId> customerIds);
}
