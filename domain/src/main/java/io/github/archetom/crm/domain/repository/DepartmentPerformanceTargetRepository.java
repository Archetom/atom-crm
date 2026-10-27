package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.DepartmentPerformanceTarget;
import io.github.archetom.crm.domain.model.PerformanceTargetMetric;
import io.github.archetom.crm.domain.model.PerformanceTargetPeriod;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.DepartmentPerformanceTargetId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped persistence port for versioned department performance targets. */
public interface DepartmentPerformanceTargetRepository {

    DepartmentPerformanceTarget save(TenantId tenantId, DepartmentPerformanceTarget target);

    Optional<DepartmentPerformanceTarget> findById(
            TenantId tenantId, DepartmentPerformanceTargetId targetId);

    Optional<DepartmentPerformanceTarget> findByIdForUpdate(
            TenantId tenantId, DepartmentPerformanceTargetId targetId);

    Optional<DepartmentPerformanceTarget> findByNaturalKey(
            TenantId tenantId,
            DepartmentId departmentId,
            PerformanceTargetMetric metric,
            int targetYear,
            PerformanceTargetPeriod period,
            int periodNumber);

    Optional<DepartmentPerformanceTarget> findByNaturalKeyForUpdate(
            TenantId tenantId,
            DepartmentId departmentId,
            PerformanceTargetMetric metric,
            int targetYear,
            PerformanceTargetPeriod period,
            int periodNumber);

    List<DepartmentPerformanceTarget> findByDepartmentAndYear(
            TenantId tenantId,
            DepartmentId departmentId,
            PerformanceTargetMetric metric,
            int targetYear);

    void delete(TenantId tenantId, DepartmentPerformanceTarget target);
}
