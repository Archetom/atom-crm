package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.Set;

/** Personal contact-star relationship inside a verified tenant membership. */
public interface ContactStarRepository {

    void setStarred(TenantId tenantId, MemberId memberId, ContactId contactId, boolean starred);

    boolean isStarred(TenantId tenantId, MemberId memberId, ContactId contactId);

    Set<ContactId> findStarred(
            TenantId tenantId, MemberId memberId, Collection<ContactId> contactIds);
}
