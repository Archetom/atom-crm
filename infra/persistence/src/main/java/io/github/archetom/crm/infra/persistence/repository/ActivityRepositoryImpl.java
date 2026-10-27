package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.Activity;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.model.ActivitySubjectSummary;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.model.ActivityStatus;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.repository.ActivityRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.ActivityId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.ActivityPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ActivityMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ActivityRelationMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ActivityPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ActivityRelationPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ActivitySubjectSummaryPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ActivityRepositoryImpl implements ActivityRepository {
    private static final int MAX_SUMMARY_SUBJECTS = 2_500;

    private final ActivityMapper mapper;
    private final ActivityRelationMapper relationMapper;
    private final ActivityPOConverter converter;

    @Override
    public Activity save(TenantId tenantId, Activity activity) {
        requireTenant(tenantId);
        if (activity == null || !tenantId.sameValueAs(activity.getTenantId())) {
            throw new IllegalArgumentException("activity tenant does not match");
        }
        ActivityPO po = converter.toPO(activity);
        if (po.getId() == null) {
            if (mapper.insert(po) != 1) throw new IllegalStateException("activity insert failed");
        } else if (mapper.update(po, new LambdaUpdateWrapper<ActivityPO>()
                .eq(ActivityPO::getTenantId, tenantId.getValue())
                .eq(ActivityPO::getId, po.getId())) != 1) {
            throw new AggregateVersionConflictException();
        }
        ActivityId id = new ActivityId(po.getId());
        relationMapper.delete(new LambdaQueryWrapper<ActivityRelationPO>()
                .eq(ActivityRelationPO::getTenantId, tenantId.getValue())
                .eq(ActivityRelationPO::getActivityId, id.getValue()));
        for (var relation : activity.getRelations()) {
            if (relationMapper.insert(converter.toPO(tenantId, id, relation)) != 1) {
                throw new IllegalStateException("activity relation insert failed");
            }
        }
        activity.onPersisted(id, po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
        return activity;
    }

    @Override
    public Optional<Activity> findById(TenantId tenantId, ActivityId id) {
        requireTenant(tenantId);
        if (id == null) return Optional.empty();
        ActivityPO root = mapper.selectOne(new LambdaQueryWrapper<ActivityPO>()
                .eq(ActivityPO::getTenantId, tenantId.getValue())
                .eq(ActivityPO::getId, id.getValue())
                .ne(ActivityPO::getStatus, ActivityStatus.DELETED.name()));
        return root == null ? Optional.empty() : Optional.of(hydrate(tenantId, root));
    }

    @Override
    public PageResult<Activity> findBySubject(TenantId tenantId, BusinessObjectType subjectType,
                                              Long subjectId, int page, int size) {
        requireTenant(tenantId);
        if (subjectType == null || subjectId == null || subjectId <= 0 || page < 1 || size < 1
                || size > 200) throw new IllegalArgumentException("activity query is invalid");
        IPage<ActivityPO> result = mapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ActivityPO>().eq(ActivityPO::getTenantId, tenantId.getValue())
                        .eq(ActivityPO::getSubjectType, subjectType.name())
                        .eq(ActivityPO::getSubjectId, subjectId)
                        .ne(ActivityPO::getStatus, ActivityStatus.DELETED.name())
                        .orderByDesc(ActivityPO::getOccurredTime).orderByDesc(ActivityPO::getId));
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(0, result.getTotal()),
                hydrate(tenantId, result.getRecords()));
    }

    @Override
    public List<ActivitySubjectSummary> findLatestBySubjects(
            TenantId tenantId, BusinessObjectType subjectType, Collection<Long> subjectIds) {
        requireTenant(tenantId);
        if (subjectType == null || subjectIds == null) {
            throw new IllegalArgumentException("activity subject summary query is invalid");
        }
        List<Long> ids = subjectIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_SUMMARY_SUBJECTS || ids.stream().anyMatch(value -> value == null || value <= 0)) {
            throw new IllegalArgumentException("activity subject summary query is invalid");
        }
        return mapper.selectLatestBySubjects(tenantId.getValue(), subjectType.name(), ids).stream()
                .map(ActivityRepositoryImpl::summary).toList();
    }

    @Override
    public DashboardMetricSummary summarizeCreatedFollowUps(
            TenantId tenantId, MemberId creatorMemberId, ReportDateRange range) {
        requireTenant(tenantId);
        if (creatorMemberId == null || range == null) {
            throw new IllegalArgumentException("activity dashboard query is invalid");
        }
        long count = mapper.selectCount(new LambdaQueryWrapper<ActivityPO>()
                .eq(ActivityPO::getTenantId, tenantId.getValue())
                .eq(ActivityPO::getCreatorActorId, creatorMemberId.getValue())
                .eq(ActivityPO::getKind, "FOLLOW_UP")
                .eq(ActivityPO::getStatus, ActivityStatus.ACTIVE.name())
                .ge(ActivityPO::getCreatedTime, range.from().atStartOfDay())
                .le(ActivityPO::getCreatedTime, range.to().atTime(23, 59, 59, 999_999_000)));
        return new DashboardMetricSummary(Math.max(0L, count), null);
    }

    @Override
    public List<Activity> findDue(TenantId tenantId, LocalDateTime fromInclusive,
                                  LocalDateTime untilExclusive, LocalDateTime afterTime,
                                  Long afterId, int limit) {
        requireTenant(tenantId);
        if (fromInclusive == null || untilExclusive == null || !fromInclusive.isBefore(untilExclusive)
                || (afterTime == null) != (afterId == null) || (afterId != null && afterId <= 0)
                || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("activity due query is invalid");
        }
        LambdaQueryWrapper<ActivityPO> query = new LambdaQueryWrapper<ActivityPO>()
                .eq(ActivityPO::getTenantId, tenantId.getValue())
                .eq(ActivityPO::getStatus, ActivityStatus.ACTIVE.name())
                .isNotNull(ActivityPO::getNextContactTime)
                .ge(ActivityPO::getNextContactTime, fromInclusive)
                .lt(ActivityPO::getNextContactTime, untilExclusive);
        if (afterTime != null) {
            query.and(wrapper -> wrapper.gt(ActivityPO::getNextContactTime, afterTime)
                    .or(next -> next.eq(ActivityPO::getNextContactTime, afterTime)
                            .gt(ActivityPO::getId, afterId)));
        }
        List<ActivityPO> roots = mapper.selectList(query.orderByAsc(ActivityPO::getNextContactTime)
                .orderByAsc(ActivityPO::getId).last("LIMIT " + limit));
        return hydrate(tenantId, roots);
    }

    private Activity hydrate(TenantId tenantId, ActivityPO root) {
        return converter.toDomain(root, relationMapper.selectList(new LambdaQueryWrapper<ActivityRelationPO>()
                .eq(ActivityRelationPO::getTenantId, tenantId.getValue())
                .eq(ActivityRelationPO::getActivityId, root.getId()).orderByAsc(ActivityRelationPO::getId)));
    }

    private List<Activity> hydrate(TenantId tenantId, List<ActivityPO> roots) {
        if (roots == null || roots.isEmpty()) return List.of();
        List<Long> ids = roots.stream().map(ActivityPO::getId).toList();
        Map<Long, List<ActivityRelationPO>> relations = relationMapper.selectList(
                new LambdaQueryWrapper<ActivityRelationPO>().eq(ActivityRelationPO::getTenantId,
                        tenantId.getValue()).in(ActivityRelationPO::getActivityId, ids)
                        .orderByAsc(ActivityRelationPO::getId)).stream()
                .collect(Collectors.groupingBy(ActivityRelationPO::getActivityId));
        return roots.stream().map(root -> converter.toDomain(root,
                relations.getOrDefault(root.getId(), List.of()))).toList();
    }

    private static ActivitySubjectSummary summary(ActivitySubjectSummaryPO value) {
        return new ActivitySubjectSummary(value.getSubjectId(), value.getOccurredTime(), value.getContent());
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("activity tenant is required");
    }
}
