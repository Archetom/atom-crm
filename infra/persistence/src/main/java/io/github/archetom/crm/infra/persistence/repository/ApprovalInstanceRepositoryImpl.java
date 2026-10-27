package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.entity.ApprovalTask;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.ApprovalAlreadyExistsException;
import io.github.archetom.crm.domain.model.ApprovalInstanceQuery;
import io.github.archetom.crm.domain.model.ApprovalInstanceReference;
import io.github.archetom.crm.domain.model.ApprovalInstanceSummary;
import io.github.archetom.crm.domain.model.ApprovalInstanceStatus;
import io.github.archetom.crm.domain.model.ApprovalSubjectStatus;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.repository.ApprovalInstanceRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.ApprovalInstancePOConverter;
import io.github.archetom.crm.infra.persistence.converter.ApprovalTaskPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ApprovalInstanceMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ApprovalManagerMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ApprovalTaskMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ApprovalWatcherMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalInstancePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalInstanceReferencePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalManagerPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalTaskPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalWatcherPO;
import io.github.archetom.crm.infra.persistence.support.SqlLikePattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ApprovalInstanceRepositoryImpl implements ApprovalInstanceRepository {

    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalTaskMapper taskMapper;
    private final ApprovalManagerMapper managerMapper;
    private final ApprovalWatcherMapper watcherMapper;
    private final ApprovalInstancePOConverter instanceConverter;
    private final ApprovalTaskPOConverter taskConverter;

    @Override
    public ApprovalInstance save(TenantId tenantId, ApprovalInstance instance) {
        requireTenantMatch(tenantId, instance);
        ApprovalInstancePO po = instanceConverter.toPO(instance);
        boolean create = po.getId() == null;
        try {
            if (create) {
                if (instanceMapper.insert(po) != 1) {
                    throw new IllegalStateException("Approval instance insert did not affect one row");
                }
                instance.onPersisted(new ApprovalInstanceId(po.getId()), po.getVersion(),
                        po.getCreatedTime(), po.getUpdatedTime());
                insertManagers(tenantId, po.getId(), instance.getManagerMemberIds());
                insertWatchers(tenantId, po.getId(), instance.getWatcherMemberIds());
            } else if (instanceMapper.update(po, instanceFilter(tenantId)
                    .eq(ApprovalInstancePO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            } else {
                instance.onPersisted(new ApprovalInstanceId(po.getId()), po.getVersion(),
                        po.getCreatedTime(), po.getUpdatedTime());
            }
            persistTasks(tenantId, po.getId(), instance.getTasks());
            return instance;
        } catch (DuplicateKeyException exception) {
            throw new ApprovalAlreadyExistsException();
        }
    }

    @Override
    public Optional<ApprovalInstance> findById(
            TenantId tenantId, ApprovalInstanceId instanceId) {
        requireTenant(tenantId);
        if (instanceId == null) {
            return Optional.empty();
        }
        ApprovalInstancePO po = instanceMapper.selectOne(instanceFilter(tenantId)
                .eq(ApprovalInstancePO::getId, instanceId.getValue()));
        return hydrate(tenantId, po == null ? List.of() : List.of(po), false).stream().findFirst();
    }

    @Override
    public Optional<ApprovalInstance> findByIdForUpdate(
            TenantId tenantId, ApprovalInstanceId instanceId) {
        requireTenant(tenantId);
        if (instanceId == null) {
            return Optional.empty();
        }
        ApprovalInstancePO po = instanceMapper.selectOne(instanceFilter(tenantId)
                .eq(ApprovalInstancePO::getId, instanceId.getValue()).last("FOR UPDATE"));
        // The root lock alone does not refresh a REPEATABLE_READ snapshot established by an
        // earlier authorization query. Approval-task state determines the current assignee, so
        // hydrate it with a locking current read before any caller is admitted to mutate a child.
        return hydrate(tenantId, po == null ? List.of() : List.of(po), true).stream().findFirst();
    }

    @Override
    public Optional<ApprovalInstance> findByIdempotencyKey(
            TenantId tenantId, String idempotencyKey) {
        requireTenant(tenantId);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        ApprovalInstancePO po = instanceMapper.selectOne(instanceFilter(tenantId)
                .eq(ApprovalInstancePO::getIdempotencyKey, idempotencyKey));
        return hydrate(tenantId, po == null ? List.of() : List.of(po), false).stream().findFirst();
    }

    @Override
    public List<ApprovalInstanceSummary> findSummariesByIds(
            TenantId tenantId, Collection<ApprovalInstanceId> instanceIds) {
        requireTenant(tenantId);
        LinkedHashSet<Long> ids = instanceIds(instanceIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, ApprovalInstanceSummary> byId = instanceMapper.selectList(
                        instanceFilter(tenantId).in(ApprovalInstancePO::getId, ids))
                .stream().map(instanceConverter::toSummary).collect(Collectors.toMap(
                        summary -> summary.id().getValue(), summary -> summary));
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public List<ApprovalInstance> findByIds(
            TenantId tenantId, Collection<ApprovalInstanceId> instanceIds) {
        requireTenant(tenantId);
        LinkedHashSet<Long> ids = instanceIds(instanceIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, ApprovalInstance> byId = hydrate(tenantId, instanceMapper.selectList(
                        instanceFilter(tenantId).in(ApprovalInstancePO::getId, ids)), false)
                .stream().collect(Collectors.toMap(value -> value.getId().getValue(), value -> value));
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public List<ApprovalSubjectStatus> findLatestSubjectStatuses(
            TenantId tenantId, BusinessObjectType objectType, Collection<Long> subjectIds) {
        requireTenant(tenantId);
        if (objectType == null) {
            throw new IllegalArgumentException("business object type is required");
        }
        LinkedHashSet<Long> ids = subjectIds(subjectIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return instanceMapper.selectLatestSubjectStatuses(tenantId.getValue(), objectType.name(), ids)
                .stream().map(value -> new ApprovalSubjectStatus(value.getSubjectId(),
                        ApprovalInstanceStatus.valueOf(value.getStatus())))
                .toList();
    }

    @Override
    public PageResult<ApprovalInstanceReference> scanPage(
            TenantId tenantId, ApprovalInstanceQuery query) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("approval query is required");
        }
        IPage<ApprovalInstanceReferencePO> page = instanceMapper.selectApprovalScanPage(
                new Page<>(query.page(), query.size()), tenantId.getValue(),
                query.memberId().getValue(), query.bucket().name(),
                query.status() == null ? null : query.status().name(),
                query.objectType() == null ? null : query.objectType().name(),
                query.subjectId(), SqlLikePattern.literal(query.keyword()));
        return new PageResult<>(page.getCurrent(), page.getSize(), Math.max(0, page.getTotal()),
                page.getRecords().stream().map(po -> new ApprovalInstanceReference(
                        new ApprovalInstanceId(po.getId()),
                        BusinessObjectType.valueOf(po.getObjectType()), po.getSubjectId())).toList());
    }

    private List<ApprovalInstance> hydrate(
            TenantId tenantId, List<ApprovalInstancePO> roots, boolean lockTasks) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        List<Long> ids = roots.stream().map(ApprovalInstancePO::getId).toList();
        LambdaQueryWrapper<ApprovalTaskPO> taskQuery = new LambdaQueryWrapper<ApprovalTaskPO>()
                .eq(ApprovalTaskPO::getTenantId, tenantId.getValue())
                .in(ApprovalTaskPO::getInstanceId, ids)
                .orderByAsc(ApprovalTaskPO::getStepOrder,
                        ApprovalTaskPO::getActorOrder,
                        ApprovalTaskPO::getCreatedTime);
        if (lockTasks) {
            taskQuery.last("FOR UPDATE");
        }
        Map<Long, List<ApprovalTask>> tasks = taskMapper.selectList(taskQuery)
                .stream().collect(Collectors.groupingBy(ApprovalTaskPO::getInstanceId,
                        LinkedHashMap::new, Collectors.mapping(taskConverter::toDomain,
                                Collectors.toList())));
        Map<Long, Set<MemberId>> watchers = watcherMapper.selectList(
                        new LambdaQueryWrapper<ApprovalWatcherPO>()
                                .eq(ApprovalWatcherPO::getTenantId, tenantId.getValue())
                                .in(ApprovalWatcherPO::getInstanceId, ids)
                                .orderByAsc(ApprovalWatcherPO::getInstanceId,
                                        ApprovalWatcherPO::getActorId))
                .stream().collect(Collectors.groupingBy(ApprovalWatcherPO::getInstanceId,
                        LinkedHashMap::new, Collectors.mapping(
                                po -> new MemberId(po.getActorId()),
                                Collectors.toCollection(LinkedHashSet::new))));
        Map<Long, Set<MemberId>> managers = managerMapper.selectList(
                        new LambdaQueryWrapper<ApprovalManagerPO>()
                                .eq(ApprovalManagerPO::getTenantId, tenantId.getValue())
                                .in(ApprovalManagerPO::getInstanceId, ids)
                                .orderByAsc(ApprovalManagerPO::getInstanceId,
                                        ApprovalManagerPO::getActorId))
                .stream().collect(Collectors.groupingBy(ApprovalManagerPO::getInstanceId,
                        LinkedHashMap::new, Collectors.mapping(
                                po -> new MemberId(po.getActorId()),
                                Collectors.toCollection(LinkedHashSet::new))));
        return roots.stream().map(root -> instanceConverter.toDomain(root,
                tasks.getOrDefault(root.getId(), List.of()),
                managers.getOrDefault(root.getId(), Set.of()),
                watchers.getOrDefault(root.getId(), Set.of()))).toList();
    }

    private void persistTasks(TenantId tenantId, Long instanceId,
                              Collection<ApprovalTask> tasks) {
        for (ApprovalTask task : tasks) {
            ApprovalTaskPO po = taskConverter.toPO(tenantId, instanceId, task);
            if (task.getCreatedTime() == null) {
                if (taskMapper.insert(po) != 1) {
                    throw new IllegalStateException("Approval task insert did not affect one row");
                }
            } else if (!task.hasPendingPersistenceChanges()) {
                continue;
            } else if (taskMapper.update(po, new LambdaQueryWrapper<ApprovalTaskPO>()
                    .eq(ApprovalTaskPO::getTenantId, tenantId.getValue())
                    .eq(ApprovalTaskPO::getTaskKey, task.getTaskKey())) != 1) {
                throw new AggregateVersionConflictException();
            }
            task.onPersisted(po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
        }
    }

    private void insertWatchers(TenantId tenantId, Long instanceId,
                                Collection<MemberId> watchers) {
        for (MemberId watcher : watchers) {
            ApprovalWatcherPO po = new ApprovalWatcherPO();
            po.setTenantId(tenantId.getValue());
            po.setInstanceId(instanceId);
            po.setActorId(watcher.getValue());
            watcherMapper.insert(po);
        }
    }

    private void insertManagers(TenantId tenantId, Long instanceId,
                                Collection<MemberId> managers) {
        for (MemberId manager : managers) {
            ApprovalManagerPO po = new ApprovalManagerPO();
            po.setTenantId(tenantId.getValue());
            po.setInstanceId(instanceId);
            po.setActorId(manager.getValue());
            managerMapper.insert(po);
        }
    }

    private LambdaQueryWrapper<ApprovalInstancePO> instanceFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<ApprovalInstancePO>()
                .eq(ApprovalInstancePO::getTenantId, tenantId.getValue());
    }

    private LinkedHashSet<Long> instanceIds(
            Collection<ApprovalInstanceId> instanceIds) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (instanceIds == null) {
            return ids;
        }
        for (ApprovalInstanceId instanceId : instanceIds) {
            if (instanceId == null) {
                throw new IllegalArgumentException("approval instance ID is required");
            }
            ids.add(instanceId.getValue());
        }
        return ids;
    }

    private LinkedHashSet<Long> subjectIds(Collection<Long> subjectIds) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (subjectIds == null) {
            return ids;
        }
        for (Long subjectId : subjectIds) {
            if (subjectId == null || subjectId <= 0) {
                throw new IllegalArgumentException("approval subject ID is required");
            }
            ids.add(subjectId);
        }
        return ids;
    }

    private void requireTenantMatch(TenantId tenantId, ApprovalInstance instance) {
        requireTenant(tenantId);
        if (instance == null || instance.getTenantId() == null
                || !tenantId.sameValueAs(instance.getTenantId())) {
            throw new IllegalArgumentException("approval instance tenant does not match");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID is required");
        }
    }
}
