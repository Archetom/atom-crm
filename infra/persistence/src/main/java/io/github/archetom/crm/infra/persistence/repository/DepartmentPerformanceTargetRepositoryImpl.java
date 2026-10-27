package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.domain.entity.DepartmentPerformanceTarget;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.DepartmentPerformanceTargetAlreadyExistsException;
import io.github.archetom.crm.domain.model.PerformanceTargetMetric;
import io.github.archetom.crm.domain.model.PerformanceTargetPeriod;
import io.github.archetom.crm.domain.repository.DepartmentPerformanceTargetRepository;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.DepartmentPerformanceTargetId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.DepartmentPerformanceTargetPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.DepartmentPerformanceTargetMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.DepartmentPerformanceTargetPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** MySQL target repository with tenant predicates and optimistic locking. */
@Repository
@RequiredArgsConstructor
public class DepartmentPerformanceTargetRepositoryImpl implements DepartmentPerformanceTargetRepository {

    private final DepartmentPerformanceTargetMapper mapper;
    private final DepartmentPerformanceTargetPOConverter converter;

    @Override
    public DepartmentPerformanceTarget save(TenantId tenantId, DepartmentPerformanceTarget target) {
        requireTenantMatch(tenantId, target);
        DepartmentPerformanceTargetPO po = converter.toPO(target);
        try {
            if (po.getId() == null) {
                if (mapper.insert(po) != 1) {
                    throw new IllegalStateException("department performance target insert did not affect exactly one row");
                }
            } else if (mapper.update(po, tenantFilter(tenantId)
                    .eq(DepartmentPerformanceTargetPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
        } catch (DuplicateKeyException exception) {
            throw new DepartmentPerformanceTargetAlreadyExistsException();
        }
        target.onPersisted(new DepartmentPerformanceTargetId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return target;
    }

    @Override
    public Optional<DepartmentPerformanceTarget> findById(
            TenantId tenantId, DepartmentPerformanceTargetId targetId) {
        if (targetId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(tenantFilter(tenantId)
                .eq(DepartmentPerformanceTargetPO::getId, targetId.getValue()))));
    }

    @Override
    public Optional<DepartmentPerformanceTarget> findByIdForUpdate(
            TenantId tenantId, DepartmentPerformanceTargetId targetId) {
        if (targetId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(tenantFilter(tenantId)
                .eq(DepartmentPerformanceTargetPO::getId, targetId.getValue()).last("FOR UPDATE"))));
    }

    @Override
    public Optional<DepartmentPerformanceTarget> findByNaturalKey(
            TenantId tenantId,
            DepartmentId departmentId,
            PerformanceTargetMetric metric,
            int targetYear,
            PerformanceTargetPeriod period,
            int periodNumber) {
        if (departmentId == null || metric == null || period == null) {
            requireTenant(tenantId);
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(tenantFilter(tenantId)
                .eq(DepartmentPerformanceTargetPO::getDepartmentId, departmentId.getValue())
                .eq(DepartmentPerformanceTargetPO::getMetric, metric.name())
                .eq(DepartmentPerformanceTargetPO::getTargetYear, targetYear)
                .eq(DepartmentPerformanceTargetPO::getPeriod, period.name())
                .eq(DepartmentPerformanceTargetPO::getPeriodNumber, periodNumber))));
    }

    @Override
    public Optional<DepartmentPerformanceTarget> findByNaturalKeyForUpdate(
            TenantId tenantId,
            DepartmentId departmentId,
            PerformanceTargetMetric metric,
            int targetYear,
            PerformanceTargetPeriod period,
            int periodNumber) {
        if (departmentId == null || metric == null || period == null) {
            requireTenant(tenantId);
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(tenantFilter(tenantId)
                .eq(DepartmentPerformanceTargetPO::getDepartmentId, departmentId.getValue())
                .eq(DepartmentPerformanceTargetPO::getMetric, metric.name())
                .eq(DepartmentPerformanceTargetPO::getTargetYear, targetYear)
                .eq(DepartmentPerformanceTargetPO::getPeriod, period.name())
                .eq(DepartmentPerformanceTargetPO::getPeriodNumber, periodNumber)
                .last("FOR UPDATE"))));
    }

    @Override
    public List<DepartmentPerformanceTarget> findByDepartmentAndYear(
            TenantId tenantId,
            DepartmentId departmentId,
            PerformanceTargetMetric metric,
            int targetYear) {
        if (departmentId == null || metric == null) {
            requireTenant(tenantId);
            return List.of();
        }
        return mapper.selectList(tenantFilter(tenantId)
                        .eq(DepartmentPerformanceTargetPO::getDepartmentId, departmentId.getValue())
                        .eq(DepartmentPerformanceTargetPO::getMetric, metric.name())
                        .eq(DepartmentPerformanceTargetPO::getTargetYear, targetYear)
                        .orderByAsc(DepartmentPerformanceTargetPO::getPeriod)
                        .orderByAsc(DepartmentPerformanceTargetPO::getPeriodNumber)
                        .orderByAsc(DepartmentPerformanceTargetPO::getId))
                .stream().map(converter::toDomain).toList();
    }

    @Override
    public void delete(TenantId tenantId, DepartmentPerformanceTarget target) {
        requireTenantMatch(tenantId, target);
        if (target.getId() == null || mapper.delete(tenantFilter(tenantId)
                .eq(DepartmentPerformanceTargetPO::getId, target.getId().getValue())
                .eq(DepartmentPerformanceTargetPO::getVersion, target.getVersion())) != 1) {
            throw new AggregateVersionConflictException();
        }
    }

    private LambdaQueryWrapper<DepartmentPerformanceTargetPO> tenantFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<DepartmentPerformanceTargetPO>()
                .eq(DepartmentPerformanceTargetPO::getTenantId, tenantId.getValue());
    }

    private static void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("department performance target tenant is required");
        }
    }

    private static void requireTenantMatch(TenantId tenantId, DepartmentPerformanceTarget target) {
        requireTenant(tenantId);
        if (target == null || target.getTenantId() == null
                || !tenantId.sameValueAs(target.getTenantId())) {
            throw new IllegalArgumentException(
                    "department performance target tenant does not match repository tenant");
        }
    }
}
