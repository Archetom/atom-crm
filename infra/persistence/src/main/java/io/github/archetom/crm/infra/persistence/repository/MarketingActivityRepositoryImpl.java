package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.MarketingActivity;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.model.MarketingActivityQuery;
import io.github.archetom.crm.domain.model.MarketingActivityStatus;
import io.github.archetom.crm.domain.repository.MarketingActivityRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.MarketingActivityId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.MarketingActivityPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.MarketingActivityMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.MarketingActivityParticipantMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.MarketingActivityParticipantDepartmentMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.MarketingActivityPO;
import io.github.archetom.crm.infra.persistence.mysql.po.MarketingActivityParticipantPO;
import io.github.archetom.crm.infra.persistence.mysql.po.MarketingActivityParticipantDepartmentPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MarketingActivityRepositoryImpl implements MarketingActivityRepository {
    private final MarketingActivityMapper activityMapper;
    private final MarketingActivityParticipantMapper participantMapper;
    private final MarketingActivityParticipantDepartmentMapper participantDepartmentMapper;
    private final MarketingActivityPOConverter converter;

    @Override
    public MarketingActivity save(TenantId tenantId, MarketingActivity activity) {
        requireTenantMatch(tenantId, activity);
        MarketingActivityPO po = converter.toPO(activity);
        if (po.getId() == null) {
            if (activityMapper.insert(po) != 1) {
                throw new IllegalStateException("marketing activity insert did not affect exactly one row");
            }
        } else if (activityMapper.update(po, tenantFilter(tenantId)
                .eq(MarketingActivityPO::getId, po.getId())) != 1) {
            throw new AggregateVersionConflictException();
        }
        MarketingActivityId activityId = new MarketingActivityId(po.getId());
        synchronizeParticipants(tenantId, activityId, activity);
        synchronizeParticipantDepartments(tenantId, activityId, activity);
        activity.onPersisted(activityId, po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
        return activity;
    }

    @Override
    public Optional<MarketingActivity> findById(TenantId tenantId, MarketingActivityId activityId) {
        if (activityId == null) return Optional.empty();
        return hydrate(tenantId, activityMapper.selectOne(tenantFilter(tenantId)
                .eq(MarketingActivityPO::getId, activityId.getValue())));
    }

    @Override
    public Optional<MarketingActivity> findByIdForUpdate(TenantId tenantId, MarketingActivityId activityId) {
        if (activityId == null) return Optional.empty();
        return hydrate(tenantId, activityMapper.selectOne(tenantFilter(tenantId)
                .eq(MarketingActivityPO::getId, activityId.getValue()).last("FOR UPDATE")));
    }

    @Override
    public PageResult<MarketingActivity> findPage(TenantId tenantId, MarketingActivityQuery query) {
        requireTenant(tenantId);
        if (query == null) throw new IllegalArgumentException("marketing activity query is required");
        LambdaQueryWrapper<MarketingActivityPO> filter = tenantFilter(tenantId)
                .eq(MarketingActivityPO::getStatus, MarketingActivityStatus.ACTIVE.name());
        if (query.keyword() != null) filter.like(MarketingActivityPO::getName, query.keyword());
        if (query.relatedObjectType() != null) {
            filter.eq(MarketingActivityPO::getRelatedObjectType, query.relatedObjectType().name());
        }
        IPage<MarketingActivityPO> page = activityMapper.selectPage(new Page<>(query.page(), query.size()),
                filter.orderByDesc(MarketingActivityPO::getUpdatedTime)
                        .orderByDesc(MarketingActivityPO::getId));
        return new PageResult<>(page.getCurrent(), page.getSize(), Math.max(0, page.getTotal()),
                hydrate(tenantId, page.getRecords()));
    }

    private Optional<MarketingActivity> hydrate(TenantId tenantId, MarketingActivityPO root) {
        if (root == null) return Optional.empty();
        List<Long> activityIds = List.of(root.getId());
        return Optional.of(converter.toDomain(root, participants(tenantId, activityIds),
                participantDepartments(tenantId, activityIds)));
    }

    private List<MarketingActivity> hydrate(TenantId tenantId, Collection<MarketingActivityPO> roots) {
        if (roots == null || roots.isEmpty()) return List.of();
        Map<Long, List<MarketingActivityParticipantPO>> byActivity = participants(tenantId,
                roots.stream().map(MarketingActivityPO::getId).toList()).stream()
                .collect(Collectors.groupingBy(MarketingActivityParticipantPO::getMarketingActivityId,
                        LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<MarketingActivityParticipantDepartmentPO>> departmentsByActivity = participantDepartments(
                tenantId, roots.stream().map(MarketingActivityPO::getId).toList()).stream()
                .collect(Collectors.groupingBy(
                        MarketingActivityParticipantDepartmentPO::getMarketingActivityId,
                        LinkedHashMap::new, Collectors.toList()));
        return roots.stream().map(root -> converter.toDomain(root,
                byActivity.getOrDefault(root.getId(), List.of()),
                departmentsByActivity.getOrDefault(root.getId(), List.of()))).toList();
    }

    private List<MarketingActivityParticipantPO> participants(TenantId tenantId, Collection<Long> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) return List.of();
        return participantMapper.selectList(new LambdaQueryWrapper<MarketingActivityParticipantPO>()
                .eq(MarketingActivityParticipantPO::getTenantId, tenantId.getValue())
                .in(MarketingActivityParticipantPO::getMarketingActivityId, activityIds)
                .orderByAsc(MarketingActivityParticipantPO::getActorId));
    }

    private List<MarketingActivityParticipantDepartmentPO> participantDepartments(
            TenantId tenantId, Collection<Long> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) return List.of();
        return participantDepartmentMapper.selectList(
                new LambdaQueryWrapper<MarketingActivityParticipantDepartmentPO>()
                        .eq(MarketingActivityParticipantDepartmentPO::getTenantId, tenantId.getValue())
                        .in(MarketingActivityParticipantDepartmentPO::getMarketingActivityId, activityIds)
                        .orderByAsc(MarketingActivityParticipantDepartmentPO::getDepartmentId));
    }

    private void synchronizeParticipants(
            TenantId tenantId, MarketingActivityId activityId, MarketingActivity activity) {
        participantMapper.delete(new LambdaQueryWrapper<MarketingActivityParticipantPO>()
                .eq(MarketingActivityParticipantPO::getTenantId, tenantId.getValue())
                .eq(MarketingActivityParticipantPO::getMarketingActivityId, activityId.getValue()));
        for (var participant : activity.getParticipantMemberIds()) {
            if (participantMapper.insert(converter.participant(tenantId, activityId, participant)) != 1) {
                throw new IllegalStateException("marketing activity participant insert failed");
            }
        }
    }

    private void synchronizeParticipantDepartments(
            TenantId tenantId, MarketingActivityId activityId, MarketingActivity activity) {
        participantDepartmentMapper.delete(new LambdaQueryWrapper<MarketingActivityParticipantDepartmentPO>()
                .eq(MarketingActivityParticipantDepartmentPO::getTenantId, tenantId.getValue())
                .eq(MarketingActivityParticipantDepartmentPO::getMarketingActivityId, activityId.getValue()));
        for (var department : activity.getParticipantDepartmentIds()) {
            if (participantDepartmentMapper.insert(
                    converter.participantDepartment(tenantId, activityId, department)) != 1) {
                throw new IllegalStateException("marketing activity participant department insert failed");
            }
        }
    }

    private LambdaQueryWrapper<MarketingActivityPO> tenantFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<MarketingActivityPO>()
                .eq(MarketingActivityPO::getTenantId, tenantId.getValue());
    }

    private static void requireTenant(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("marketing activity tenant is required");
    }

    private static void requireTenantMatch(TenantId tenantId, MarketingActivity activity) {
        requireTenant(tenantId);
        if (activity == null || activity.getTenantId() == null
                || !tenantId.sameValueAs(activity.getTenantId())) {
            throw new IllegalArgumentException("marketing activity tenant does not match repository tenant");
        }
    }
}
