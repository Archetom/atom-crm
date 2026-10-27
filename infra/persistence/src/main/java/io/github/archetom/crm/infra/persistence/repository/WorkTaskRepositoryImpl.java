package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.model.WorkTaskParticipantRole;
import io.github.archetom.crm.domain.model.WorkTaskQuery;
import io.github.archetom.crm.domain.model.WorkTaskStatus;
import io.github.archetom.crm.domain.model.WorkTaskWorklistRecord;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.WorkTaskRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import io.github.archetom.crm.infra.persistence.converter.WorkTaskPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkTaskMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkTaskReminderStepMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkTaskFollowerMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskPO;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskFollowerPO;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskReminderStepPO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Repository
public class WorkTaskRepositoryImpl implements WorkTaskRepository {

    private final WorkTaskMapper mapper;
    private final WorkTaskFollowerMapper followerMapper;
    private final WorkTaskReminderStepMapper reminderStepMapper;
    private final WorkTaskPOConverter converter;

    @Autowired
    public WorkTaskRepositoryImpl(WorkTaskMapper mapper, WorkTaskFollowerMapper followerMapper,
                                  WorkTaskReminderStepMapper reminderStepMapper,
                                  WorkTaskPOConverter converter) {
        this.mapper = mapper;
        this.followerMapper = followerMapper;
        this.reminderStepMapper = reminderStepMapper;
        this.converter = converter;
    }

    /** Compatibility constructor retained for B21 persistence unit tests. */
    public WorkTaskRepositoryImpl(WorkTaskMapper mapper, WorkTaskFollowerMapper followerMapper,
                                  WorkTaskPOConverter converter) {
        this(mapper, followerMapper, null, converter);
    }

    @Override
    public WorkTask save(TenantId tenantId, WorkTask task) {
        requireTenantMatch(tenantId, task);
        WorkTaskPO po = converter.toPO(task);
        if (po.getId() == null) {
            if (mapper.insert(po) != 1) {
                throw new IllegalStateException(
                        "Work task insert did not affect exactly one row");
            }
        } else if (mapper.update(po, new LambdaUpdateWrapper<WorkTaskPO>()
                .eq(WorkTaskPO::getTenantId, tenantId.getValue())
                .eq(WorkTaskPO::getId, po.getId())) != 1) {
            throw new AggregateVersionConflictException();
        }
        synchronizeFollowers(tenantId, new WorkTaskId(po.getId()), task);
        synchronizeReminderSteps(tenantId, new WorkTaskId(po.getId()), task);
        task.onPersisted(new WorkTaskId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return task;
    }

    @Override
    public Optional<WorkTask> findVisibleById(
            TenantId tenantId, WorkTaskId taskId,
            MemberId viewerMemberId, boolean taskManager) {
        requireTenant(tenantId);
        if (taskId == null || viewerMemberId == null) {
            return Optional.empty();
        }
        return hydrate(tenantId, mapper.selectOne(
                visible(tenantId, viewerMemberId, taskManager)
                        .eq("id", taskId.getValue())));
    }

    @Override
    public Optional<WorkTask> findVisibleByIdForUpdate(
            TenantId tenantId, WorkTaskId taskId,
            MemberId viewerMemberId, boolean taskManager) {
        requireTenant(tenantId);
        if (taskId == null || viewerMemberId == null) {
            return Optional.empty();
        }
        return hydrate(tenantId, mapper.selectOne(
                visible(tenantId, viewerMemberId, taskManager)
                        .eq("id", taskId.getValue()).last("FOR UPDATE")));
    }

    @Override
    public Optional<WorkTask> findByIdForAttachmentMutation(TenantId tenantId, WorkTaskId taskId) {
        requireTenant(tenantId);
        if (taskId == null) {
            return Optional.empty();
        }
        return hydrate(tenantId, mapper.selectForAttachmentMutation(tenantId.getValue(), taskId.getValue()));
    }

    @Override
    public Optional<WorkTask> findByIdForReminderEffect(TenantId tenantId, WorkTaskId taskId) {
        requireTenant(tenantId);
        if (taskId == null) return Optional.empty();
        return hydrate(tenantId, mapper.selectForReminderEffect(tenantId.getValue(), taskId.getValue()));
    }

    @Override
    public PageResult<WorkTask> findPage(TenantId tenantId, WorkTaskQuery query) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("work task query is required");
        }
        QueryWrapper<WorkTaskPO> filter = visible(
                tenantId, query.viewerMemberId(), query.taskManager());
        if (query.participantRole() == WorkTaskParticipantRole.CREATOR) {
            filter.eq("created_by_actor_id", query.viewerMemberId().getValue());
        } else if (query.participantRole() == WorkTaskParticipantRole.ASSIGNEE) {
            filter.eq("current_assignee_actor_id", query.viewerMemberId().getValue());
        } else if (query.participantRole() == WorkTaskParticipantRole.FOLLOWER) {
            filter.apply("EXISTS (SELECT 1 FROM crm_work_task_follower role_follower"
                            + " WHERE role_follower.tenant_id = crm_work_task.tenant_id"
                            + " AND role_follower.work_task_id = crm_work_task.id"
                            + " AND role_follower.follower_actor_id = {0})",
                    query.viewerMemberId().getValue());
        }
        if (!query.statuses().isEmpty()) {
            filter.in("status", query.statuses().stream().map(Enum::name).toList());
        }
        if (!query.priorities().isEmpty()) {
            filter.in("priority", query.priorities().stream().map(Enum::name).toList());
        }
        if (query.dueFromInclusive() != null) {
            filter.ge("due_time", query.dueFromInclusive());
        }
        if (query.dueBefore() != null) {
            filter.lt("due_time", query.dueBefore());
        }
        if (query.businessRecord() != null) {
            filter.eq("business_object_type", query.businessRecord().objectType().name())
                    .eq("business_record_id", query.businessRecord().recordId());
        }
        filter.orderByDesc("updated_time").orderByDesc("id");
        IPage<WorkTaskPO> page = mapper.selectPage(
                new Page<>(query.page(), query.size()), filter);
        return new PageResult<>(page.getCurrent(), page.getSize(),
                Math.max(0, page.getTotal()), hydrate(tenantId, page.getRecords()));
    }

    @Override
    public List<WorkTaskWorklistRecord> findDueForAssignee(
            TenantId tenantId, MemberId assigneeMemberId,
            LocalDateTime fromInclusive, LocalDateTime untilExclusive,
            LocalDateTime afterTime, Long afterId, int limit) {
        requireTenant(tenantId);
        if (assigneeMemberId == null || fromInclusive == null || untilExclusive == null
                || !fromInclusive.isBefore(untilExclusive)
                || (afterTime == null) != (afterId == null)
                || afterId != null && afterId <= 0 || limit < 1 || limit > 200) {
            throw new IllegalArgumentException("work task worklist query is invalid");
        }
        QueryWrapper<WorkTaskPO> filter = new QueryWrapper<WorkTaskPO>()
                .select("id", "worklist_projection_version", "due_time", "title")
                .eq("tenant_id", tenantId.getValue())
                .eq("current_assignee_actor_id", assigneeMemberId.getValue())
                .in("status", WorkTaskStatus.TODO.name(), WorkTaskStatus.IN_PROGRESS.name())
                .ge("due_time", fromInclusive)
                .lt("due_time", untilExclusive)
                .apply("EXISTS (SELECT 1 FROM organization_membership current_assignee"
                                + " WHERE current_assignee.tenant_id = crm_work_task.tenant_id"
                                + " AND current_assignee.actor_id = crm_work_task.current_assignee_actor_id"
                                + " AND current_assignee.status = 'ACTIVE')");
        if (afterTime != null) {
            filter.and(value -> value.gt("due_time", afterTime)
                    .or(sameTime -> sameTime.eq("due_time", afterTime).gt("id", afterId)));
        }
        filter.orderByAsc("due_time").orderByAsc("id").last("LIMIT " + limit);
        return mapper.selectList(filter).stream().map(value -> new WorkTaskWorklistRecord(
                new WorkTaskId(value.getId()), value.getWorklistProjectionVersion(),
                value.getDueTime(), value.getTitle())).toList();
    }

    private QueryWrapper<WorkTaskPO> visible(
            TenantId tenantId, MemberId viewerMemberId, boolean taskManager) {
        requireTenant(tenantId);
        if (viewerMemberId == null) {
            throw new IllegalArgumentException("work task viewer is required");
        }
        QueryWrapper<WorkTaskPO> filter = new QueryWrapper<WorkTaskPO>()
                .eq("tenant_id", tenantId.getValue())
                .ne("status", WorkTaskStatus.DELETED.name())
                .apply("EXISTS (SELECT 1 FROM organization_membership viewer"
                                + " WHERE viewer.tenant_id = crm_work_task.tenant_id"
                                + " AND viewer.actor_id = {0} AND viewer.status = 'ACTIVE')",
                        viewerMemberId.getValue());
        if (!taskManager) {
            filter.and(value -> value
                    .eq("created_by_actor_id", viewerMemberId.getValue())
                    .or()
                    .eq("current_assignee_actor_id", viewerMemberId.getValue())
                    .or()
                    .apply("EXISTS (SELECT 1 FROM crm_work_task_follower visible_follower"
                                    + " WHERE visible_follower.tenant_id = crm_work_task.tenant_id"
                                    + " AND visible_follower.work_task_id = crm_work_task.id"
                                    + " AND visible_follower.follower_actor_id = {0})",
                            viewerMemberId.getValue()));
        }
        return filter;
    }

    private Optional<WorkTask> hydrate(TenantId tenantId, WorkTaskPO root) {
        if (root == null) {
            return Optional.empty();
        }
        return Optional.of(converter.toDomain(root,
                followerActorIds(tenantId, List.of(root.getId())),
                reminderSteps(tenantId, List.of(root.getId()))));
    }

    private List<WorkTask> hydrate(TenantId tenantId, Collection<WorkTaskPO> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> followersByTask = followers(
                tenantId, roots.stream().map(WorkTaskPO::getId).toList()).stream()
                .collect(Collectors.groupingBy(WorkTaskFollowerPO::getWorkTaskId,
                        LinkedHashMap::new, Collectors.mapping(
                                WorkTaskFollowerPO::getFollowerActorId, Collectors.toList())));
        Map<Long, List<io.github.archetom.crm.domain.model.WorkTaskReminderStep>> remindersByTask =
                reminderRows(tenantId, roots.stream().map(WorkTaskPO::getId).toList()).stream()
                        .collect(Collectors.groupingBy(WorkTaskReminderStepPO::getWorkTaskId,
                                LinkedHashMap::new, Collectors.mapping(this::reminderStep,
                                        Collectors.toList())));
        return roots.stream().map(root -> converter.toDomain(root,
                followersByTask.getOrDefault(root.getId(), List.of()),
                remindersByTask.getOrDefault(root.getId(), List.of()))).toList();
    }

    private List<Long> followerActorIds(
            TenantId tenantId, Collection<Long> taskIds) {
        return followers(tenantId, taskIds).stream()
                .map(WorkTaskFollowerPO::getFollowerActorId).toList();
    }

    private List<WorkTaskFollowerPO> followers(
            TenantId tenantId, Collection<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        return followerMapper.selectList(new LambdaQueryWrapper<WorkTaskFollowerPO>()
                .eq(WorkTaskFollowerPO::getTenantId, tenantId.getValue())
                .in(WorkTaskFollowerPO::getWorkTaskId, taskIds)
                .orderByAsc(WorkTaskFollowerPO::getFollowerActorId));
    }

    private List<io.github.archetom.crm.domain.model.WorkTaskReminderStep> reminderSteps(
            TenantId tenantId, Collection<Long> taskIds) {
        return reminderRows(tenantId, taskIds).stream().map(this::reminderStep).toList();
    }

    private List<WorkTaskReminderStepPO> reminderRows(TenantId tenantId, Collection<Long> taskIds) {
        if (reminderStepMapper == null || taskIds == null || taskIds.isEmpty()) return List.of();
        return reminderStepMapper.selectList(new LambdaQueryWrapper<WorkTaskReminderStepPO>()
                .eq(WorkTaskReminderStepPO::getTenantId, tenantId.getValue())
                .in(WorkTaskReminderStepPO::getWorkTaskId, taskIds)
                .orderByAsc(WorkTaskReminderStepPO::getStepKey));
    }

    private io.github.archetom.crm.domain.model.WorkTaskReminderStep reminderStep(
            WorkTaskReminderStepPO row) {
        java.util.EnumSet<io.github.archetom.crm.domain.model.WorkTaskReminderRecipient> recipients =
                java.util.EnumSet.noneOf(io.github.archetom.crm.domain.model.WorkTaskReminderRecipient.class);
        if (Boolean.TRUE.equals(row.getNotifyAssignee())) recipients.add(
                io.github.archetom.crm.domain.model.WorkTaskReminderRecipient.ASSIGNEE);
        if (Boolean.TRUE.equals(row.getNotifyCreator())) recipients.add(
                io.github.archetom.crm.domain.model.WorkTaskReminderRecipient.CREATOR);
        if (Boolean.TRUE.equals(row.getNotifyFollowers())) recipients.add(
                io.github.archetom.crm.domain.model.WorkTaskReminderRecipient.FOLLOWERS);
        return new io.github.archetom.crm.domain.model.WorkTaskReminderStep(
                io.github.archetom.crm.domain.model.WorkTaskReminderTiming.valueOf(row.getTiming()),
                row.getOffsetMinutes(), recipients);
    }

    private void synchronizeFollowers(
            TenantId tenantId, WorkTaskId taskId, WorkTask task) {
        followerMapper.delete(new LambdaQueryWrapper<WorkTaskFollowerPO>()
                .eq(WorkTaskFollowerPO::getTenantId, tenantId.getValue())
                .eq(WorkTaskFollowerPO::getWorkTaskId, taskId.getValue()));
        task.getFollowerMemberIds().stream()
                .sorted(java.util.Comparator.comparing(MemberId::getValue))
                .forEach(follower -> {
                    WorkTaskFollowerPO row = new WorkTaskFollowerPO();
                    row.setTenantId(tenantId.getValue());
                    row.setWorkTaskId(taskId.getValue());
                    row.setFollowerActorId(follower.getValue());
                    if (followerMapper.insert(row) != 1) {
                        throw new IllegalStateException(
                                "Work task follower insert did not affect exactly one row");
                    }
                });
    }

    private void synchronizeReminderSteps(TenantId tenantId, WorkTaskId taskId, WorkTask task) {
        if (reminderStepMapper == null) return;
        reminderStepMapper.delete(new LambdaQueryWrapper<WorkTaskReminderStepPO>()
                .eq(WorkTaskReminderStepPO::getTenantId, tenantId.getValue())
                .eq(WorkTaskReminderStepPO::getWorkTaskId, taskId.getValue()));
        for (var step : task.getReminderPlan().steps()) {
            WorkTaskReminderStepPO row = new WorkTaskReminderStepPO();
            row.setTenantId(tenantId.getValue());
            row.setWorkTaskId(taskId.getValue());
            row.setStepKey(step.stepKey());
            row.setTiming(step.timing().name());
            row.setOffsetMinutes(step.offsetMinutes());
            row.setNotifyAssignee(step.recipients().contains(
                    io.github.archetom.crm.domain.model.WorkTaskReminderRecipient.ASSIGNEE));
            row.setNotifyCreator(step.recipients().contains(
                    io.github.archetom.crm.domain.model.WorkTaskReminderRecipient.CREATOR));
            row.setNotifyFollowers(step.recipients().contains(
                    io.github.archetom.crm.domain.model.WorkTaskReminderRecipient.FOLLOWERS));
            if (reminderStepMapper.insert(row) != 1) {
                throw new IllegalStateException("Work task reminder step insert did not affect exactly one row");
            }
        }
    }

    private void requireTenantMatch(TenantId tenantId, WorkTask task) {
        requireTenant(tenantId);
        if (task == null || task.getTenantId() == null
                || !tenantId.sameValueAs(task.getTenantId())) {
            throw new IllegalArgumentException(
                    "work task tenant does not match repository tenant");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID must not be null");
        }
    }
}
