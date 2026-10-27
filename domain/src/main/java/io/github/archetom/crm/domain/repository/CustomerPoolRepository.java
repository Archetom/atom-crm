package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.CustomerPool;
import io.github.archetom.crm.domain.entity.CustomerPoolMembership;
import io.github.archetom.crm.domain.entity.CustomerPoolRule;
import io.github.archetom.crm.domain.model.CustomerPoolHistorySummary;
import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.CustomerPoolId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Tenant-scoped persistence and database arbitration for customer pools. */
public interface CustomerPoolRepository {

    CustomerPool save(TenantId tenantId, CustomerPool pool);

    Optional<CustomerPool> findById(TenantId tenantId, CustomerPoolId poolId);

    Optional<CustomerPool> findByIdForUpdate(TenantId tenantId, CustomerPoolId poolId);

    PageResult<CustomerPool> findAll(TenantId tenantId, int page, int size);

    List<CustomerPool> findAutomaticReleaseAfter(
            TenantId tenantId, CustomerPoolId afterPoolId, int limit);

    boolean hasActiveAutomaticRelease(TenantId tenantId);

    CustomerPoolId lockAutomationCursor(TenantId tenantId);

    void saveAutomationCursor(TenantId tenantId, CustomerPoolId lastPoolId);

    boolean existsByName(TenantId tenantId, String name, CustomerPoolId excludedId);

    long countCustomers(TenantId tenantId, CustomerPoolId poolId);

    Map<CustomerPoolId, Long> countCustomers(
            TenantId tenantId, Collection<CustomerPoolId> poolIds);

    void addMembership(CustomerPoolMembership membership);

    Optional<CustomerPoolMembership> findMembershipForUpdate(
            TenantId tenantId, CustomerPoolId poolId, CustomerId customerId);

    void removeMembership(TenantId tenantId, CustomerPoolId poolId, CustomerId customerId);

    int transferMemberships(TenantId tenantId, CustomerPoolId sourcePoolId,
                            CustomerPoolId targetPoolId);

    int countClaims(TenantId tenantId, CustomerPoolId poolId, MemberId memberId, LocalDate claimDate);

    void recordClaim(TenantId tenantId, CustomerPoolId poolId, CustomerId customerId,
                     MemberId memberId, LocalDateTime claimedTime, boolean assigned);

    void recordRelease(TenantId tenantId, CustomerPoolId poolId, CustomerId customerId,
                       MemberId previousOwnerMemberId, LocalDateTime releasedTime);

    /**
     * Counts immutable release and direct-claim events only in the caller's accessible pools and
     * within the supplied customer owner data scope. Collaborator grants cannot be retroactively
     * projected onto an event that has no durable collaborator snapshot.
     */
    CustomerPoolHistorySummary summarizeEventHistory(
            TenantId tenantId, Collection<CustomerPoolId> accessiblePoolIds,
            CustomerVisibility visibility, ReportDateRange range);

    List<CustomerId> findAutomationCandidates(
            TenantId tenantId, CustomerPool pool, CustomerPoolRule rule,
            LocalDateTime cutoff, int limit);
}
