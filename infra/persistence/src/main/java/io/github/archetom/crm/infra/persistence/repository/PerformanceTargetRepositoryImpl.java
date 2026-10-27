package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.domain.entity.PerformanceTarget;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.PerformanceTargetAlreadyExistsException;
import io.github.archetom.crm.domain.model.PerformanceTargetMetric;
import io.github.archetom.crm.domain.repository.PerformanceTargetRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.PerformanceTargetId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.PerformanceTargetPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.PerformanceTargetMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.PerformanceTargetPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** MySQL target repository with tenant-only predicates and optimistic locking. */
@Repository
@RequiredArgsConstructor
public class PerformanceTargetRepositoryImpl implements PerformanceTargetRepository {

    private final PerformanceTargetMapper mapper;
    private final PerformanceTargetPOConverter converter;

    @Override
    public PerformanceTarget save(TenantId tenantId, PerformanceTarget target) {
        requireTenantMatch(tenantId, target);
        PerformanceTargetPO po = converter.toPO(target);
        try {
            if (po.getId() == null) {
                if (mapper.insert(po) != 1) {
                    throw new IllegalStateException("performance target insert did not affect exactly one row");
                }
            } else if (mapper.update(po, tenantFilter(tenantId)
                    .eq(PerformanceTargetPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
        } catch (DuplicateKeyException exception) {
            throw new PerformanceTargetAlreadyExistsException();
        }
        target.onPersisted(new PerformanceTargetId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return target;
    }

    @Override
    public Optional<PerformanceTarget> findById(TenantId tenantId, PerformanceTargetId targetId) {
        if (targetId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(tenantFilter(tenantId)
                .eq(PerformanceTargetPO::getId, targetId.getValue()))));
    }

    @Override
    public Optional<PerformanceTarget> findByIdForUpdate(
            TenantId tenantId, PerformanceTargetId targetId) {
        if (targetId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(tenantFilter(tenantId)
                .eq(PerformanceTargetPO::getId, targetId.getValue()).last("FOR UPDATE"))));
    }

    @Override
    public Optional<PerformanceTarget> findByNaturalKey(
            TenantId tenantId, MemberId memberId, PerformanceTargetMetric metric,
            int targetYear, int targetMonth) {
        if (memberId == null || metric == null) {
            requireTenant(tenantId);
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(tenantFilter(tenantId)
                .eq(PerformanceTargetPO::getActorId, memberId.getValue())
                .eq(PerformanceTargetPO::getMetric, metric.name())
                .eq(PerformanceTargetPO::getTargetYear, targetYear)
                .eq(PerformanceTargetPO::getTargetMonth, targetMonth))));
    }

    @Override
    public Optional<PerformanceTarget> findByNaturalKeyForUpdate(
            TenantId tenantId, MemberId memberId, PerformanceTargetMetric metric,
            int targetYear, int targetMonth) {
        if (memberId == null || metric == null) {
            requireTenant(tenantId);
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(tenantFilter(tenantId)
                .eq(PerformanceTargetPO::getActorId, memberId.getValue())
                .eq(PerformanceTargetPO::getMetric, metric.name())
                .eq(PerformanceTargetPO::getTargetYear, targetYear)
                .eq(PerformanceTargetPO::getTargetMonth, targetMonth)
                .last("FOR UPDATE"))));
    }

    @Override
    public List<PerformanceTarget> findByMemberAndYear(
            TenantId tenantId, MemberId memberId, PerformanceTargetMetric metric, int targetYear) {
        if (memberId == null || metric == null) {
            requireTenant(tenantId);
            return List.of();
        }
        return mapper.selectList(tenantFilter(tenantId)
                        .eq(PerformanceTargetPO::getActorId, memberId.getValue())
                        .eq(PerformanceTargetPO::getMetric, metric.name())
                        .eq(PerformanceTargetPO::getTargetYear, targetYear)
                        .orderByAsc(PerformanceTargetPO::getTargetMonth)
                        .orderByAsc(PerformanceTargetPO::getId))
                .stream().map(converter::toDomain).toList();
    }

    @Override
    public void delete(TenantId tenantId, PerformanceTarget target) {
        requireTenantMatch(tenantId, target);
        if (target.getId() == null || mapper.delete(tenantFilter(tenantId)
                .eq(PerformanceTargetPO::getId, target.getId().getValue())
                .eq(PerformanceTargetPO::getVersion, target.getVersion())) != 1) {
            throw new AggregateVersionConflictException();
        }
    }

    private LambdaQueryWrapper<PerformanceTargetPO> tenantFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<PerformanceTargetPO>()
                .eq(PerformanceTargetPO::getTenantId, tenantId.getValue());
    }

    private static void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("performance target tenant is required");
        }
    }

    private static void requireTenantMatch(TenantId tenantId, PerformanceTarget target) {
        requireTenant(tenantId);
        if (target == null || target.getTenantId() == null
                || !tenantId.sameValueAs(target.getTenantId())) {
            throw new IllegalArgumentException("performance target tenant does not match repository tenant");
        }
    }
}
