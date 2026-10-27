package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.PerformanceTarget;
import io.github.archetom.crm.domain.model.PerformanceTargetMetric;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.PerformanceTargetId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped persistence port for versioned member monthly performance targets. */
public interface PerformanceTargetRepository {

    PerformanceTarget save(TenantId tenantId, PerformanceTarget target);

    Optional<PerformanceTarget> findById(TenantId tenantId, PerformanceTargetId targetId);

    Optional<PerformanceTarget> findByIdForUpdate(TenantId tenantId, PerformanceTargetId targetId);

    Optional<PerformanceTarget> findByNaturalKey(
            TenantId tenantId,
            MemberId memberId,
            PerformanceTargetMetric metric,
            int targetYear,
            int targetMonth);

    Optional<PerformanceTarget> findByNaturalKeyForUpdate(
            TenantId tenantId,
            MemberId memberId,
            PerformanceTargetMetric metric,
            int targetYear,
            int targetMonth);

    List<PerformanceTarget> findByMemberAndYear(
            TenantId tenantId,
            MemberId memberId,
            PerformanceTargetMetric metric,
            int targetYear);

    void delete(TenantId tenantId, PerformanceTarget target);
}
