package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.ContactPool;
import io.github.archetom.crm.domain.entity.ContactPoolMembership;
import io.github.archetom.crm.domain.entity.ContactPoolRule;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContactPoolId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Tenant-scoped persistence and database arbitration for contact pools. */
public interface ContactPoolRepository {

    ContactPool save(TenantId tenantId, ContactPool pool);

    Optional<ContactPool> findById(TenantId tenantId, ContactPoolId poolId);

    Optional<ContactPool> findByIdForUpdate(TenantId tenantId, ContactPoolId poolId);

    PageResult<ContactPool> findAll(TenantId tenantId, int page, int size);

    List<ContactPool> findAutomaticReleaseAfter(
            TenantId tenantId, ContactPoolId afterPoolId, int limit);

    boolean hasActiveAutomaticRelease(TenantId tenantId);

    ContactPoolId lockAutomationCursor(TenantId tenantId);

    void saveAutomationCursor(TenantId tenantId, ContactPoolId lastPoolId);

    boolean existsByName(TenantId tenantId, String name, ContactPoolId excludedId);

    long countContacts(TenantId tenantId, ContactPoolId poolId);

    Map<ContactPoolId, Long> countContacts(
            TenantId tenantId, Collection<ContactPoolId> poolIds);

    void addMembership(ContactPoolMembership membership);

    Optional<ContactPoolMembership> findMembershipForUpdate(
            TenantId tenantId, ContactPoolId poolId, ContactId contactId);

    void removeMembership(TenantId tenantId, ContactPoolId poolId, ContactId contactId);

    int transferMemberships(TenantId tenantId, ContactPoolId sourcePoolId,
                            ContactPoolId targetPoolId);

    int countClaims(TenantId tenantId, ContactPoolId poolId, MemberId memberId, LocalDate claimDate);

    void recordClaim(TenantId tenantId, ContactPoolId poolId, ContactId contactId,
                     MemberId memberId, LocalDateTime claimedTime, boolean assigned);

    List<ContactId> findAutomationCandidates(
            TenantId tenantId, ContactPool pool, ContactPoolRule rule,
            LocalDateTime cutoff, int limit);
}
