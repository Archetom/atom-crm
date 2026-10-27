package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.WorkTaskSeries;
import io.github.archetom.crm.domain.entity.WorkTaskSeriesOccurrence;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.model.WorkTaskSeriesStatus;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.WorkTaskSeriesRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskSeriesId;
import io.github.archetom.crm.infra.persistence.converter.WorkTaskSeriesPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkTaskMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkTaskSeriesFollowerMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkTaskSeriesMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkTaskSeriesReminderStepMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkTaskSeriesOccurrenceMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkTaskSeriesWeekdayMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskPO;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskSeriesFollowerPO;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskSeriesOccurrencePO;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskSeriesPO;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskSeriesWeekdayPO;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskSeriesReminderStepPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class WorkTaskSeriesRepositoryImpl implements WorkTaskSeriesRepository {
    private final WorkTaskSeriesMapper mapper;
    private final WorkTaskSeriesWeekdayMapper weekdayMapper;
    private final WorkTaskSeriesFollowerMapper followerMapper;
    private final WorkTaskSeriesOccurrenceMapper occurrenceMapper;
    private final WorkTaskSeriesReminderStepMapper reminderStepMapper;
    private final WorkTaskMapper taskMapper;
    private final WorkTaskSeriesPOConverter converter;

    @Override
    public WorkTaskSeries save(TenantId tenantId, WorkTaskSeries series) {
        requireTenant(tenantId);
        if (series == null || !tenantId.equals(series.getTenantId())) {
            throw new IllegalArgumentException("work task series tenant is invalid");
        }
        WorkTaskSeriesPO po = converter.toPO(series);
        if (po.getId() == null) {
            if (mapper.insert(po) != 1) throw new IllegalStateException("work task series insert failed");
        } else if (mapper.update(po, new LambdaUpdateWrapper<WorkTaskSeriesPO>()
                .eq(WorkTaskSeriesPO::getTenantId, tenantId.getValue())
                .eq(WorkTaskSeriesPO::getId, po.getId())) != 1) {
            throw new AggregateVersionConflictException();
        }
        synchronizeWeekdays(tenantId, po.getId(), series);
        synchronizeFollowers(tenantId, po.getId(), series);
        synchronizeReminderSteps(tenantId, po.getId(), series);
        series.onPersisted(new WorkTaskSeriesId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return series;
    }

    @Override
    public Optional<WorkTaskSeries> findVisibleById(
            TenantId tenantId, WorkTaskSeriesId seriesId, MemberId viewer, boolean manager) {
        if (seriesId == null || viewer == null) return Optional.empty();
        return hydrate(tenantId, mapper.selectOne(visible(tenantId, viewer, manager).eq("id", seriesId.getValue())));
    }

    @Override
    public Optional<WorkTaskSeries> findVisibleByIdForUpdate(
            TenantId tenantId, WorkTaskSeriesId seriesId, MemberId viewer, boolean manager) {
        if (seriesId == null || viewer == null) return Optional.empty();
        return hydrate(tenantId, mapper.selectOne(visible(tenantId, viewer, manager)
                .eq("id", seriesId.getValue()).last("FOR UPDATE")));
    }

    @Override
    public Optional<WorkTaskSeries> findByIdForUpdate(TenantId tenantId, WorkTaskSeriesId seriesId) {
        requireTenant(tenantId);
        if (seriesId == null) return Optional.empty();
        return hydrate(tenantId, mapper.selectOne(new QueryWrapper<WorkTaskSeriesPO>()
                .eq("tenant_id", tenantId.getValue()).eq("id", seriesId.getValue()).last("FOR UPDATE")));
    }

    @Override
    public PageResult<WorkTaskSeries> findPage(
            TenantId tenantId, long page, long size, MemberId viewer, boolean manager) {
        if (page < 1 || size < 1 || size > 200 || viewer == null) {
            throw new IllegalArgumentException("work task series page is invalid");
        }
        QueryWrapper<WorkTaskSeriesPO> query = visible(tenantId, viewer, manager)
                .orderByDesc("updated_time").orderByDesc("id");
        IPage<WorkTaskSeriesPO> result = mapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(0, result.getTotal()),
                hydrate(tenantId, result.getRecords()));
    }

    @Override
    public PageResult<WorkTaskSeriesOccurrence> findOccurrences(
            TenantId tenantId, WorkTaskSeriesId seriesId, long page, long size) {
        requireTenant(tenantId);
        if (seriesId == null || page < 1 || size < 1 || size > 200) {
            throw new IllegalArgumentException("work task series occurrence page is invalid");
        }
        IPage<WorkTaskSeriesOccurrencePO> result = occurrenceMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<WorkTaskSeriesOccurrencePO>().eq("tenant_id", tenantId.getValue())
                        .eq("work_task_series_id", seriesId.getValue()).orderByDesc("id"));
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(0, result.getTotal()),
                result.getRecords().stream().map(converter::toDomain).toList());
    }

    @Override
    public List<WorkTaskSeries> findDueForUpdate(TenantId tenantId, LocalDateTime evaluationUtc, int limit) {
        requireTenant(tenantId);
        if (evaluationUtc == null || limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("work task series due query is invalid");
        }
        List<WorkTaskSeriesPO> roots = mapper.selectList(new QueryWrapper<WorkTaskSeriesPO>()
                .eq("tenant_id", tenantId.getValue()).eq("status", WorkTaskSeriesStatus.ACTIVE.name())
                .le("next_due_at", evaluationUtc).orderByAsc("id").last("LIMIT " + limit + " FOR UPDATE"));
        return hydrate(tenantId, roots);
    }

    @Override
    public boolean hasActive(TenantId tenantId) {
        requireTenant(tenantId);
        return mapper.selectCount(new QueryWrapper<WorkTaskSeriesPO>().eq("tenant_id", tenantId.getValue())
                .eq("status", WorkTaskSeriesStatus.ACTIVE.name())) > 0;
    }

    @Override
    public long countLive(TenantId tenantId) {
        requireTenant(tenantId);
        return mapper.selectCount(new QueryWrapper<WorkTaskSeriesPO>().eq("tenant_id", tenantId.getValue())
                .ne("status", WorkTaskSeriesStatus.DELETED.name()));
    }

    @Override
    public boolean hasOpenGeneratedTask(TenantId tenantId, WorkTaskSeriesId seriesId) {
        requireTenant(tenantId);
        if (seriesId == null) return false;
        return taskMapper.selectCount(new QueryWrapper<WorkTaskPO>().eq("tenant_id", tenantId.getValue())
                .eq("origin_kind", "RECURRENCE").eq("origin_series_id", seriesId.getValue())
                .in("status", "TODO", "IN_PROGRESS")) > 0;
    }

    @Override
    public WorkTaskSeriesOccurrence appendOccurrence(TenantId tenantId, WorkTaskSeriesOccurrence occurrence) {
        requireTenant(tenantId);
        if (occurrence == null || !tenantId.equals(occurrence.tenantId())) {
            throw new IllegalArgumentException("work task series occurrence tenant is invalid");
        }
        WorkTaskSeriesOccurrencePO po = converter.toPO(occurrence);
        if (occurrenceMapper.insert(po) != 1) throw new IllegalStateException("work task series occurrence insert failed");
        return converter.toDomain(po);
    }

    private QueryWrapper<WorkTaskSeriesPO> visible(TenantId tenantId, MemberId viewer, boolean manager) {
        requireTenant(tenantId);
        QueryWrapper<WorkTaskSeriesPO> query = new QueryWrapper<WorkTaskSeriesPO>()
                .eq("tenant_id", tenantId.getValue()).ne("status", WorkTaskSeriesStatus.DELETED.name())
                .apply("EXISTS (SELECT 1 FROM organization_membership viewer"
                                + " WHERE viewer.tenant_id = crm_work_task_series.tenant_id"
                                + " AND viewer.actor_id = {0} AND viewer.status = 'ACTIVE')", viewer.getValue());
        if (!manager) {
            query.and(value -> value.eq("created_by_actor_id", viewer.getValue())
                    .or().eq("current_assignee_actor_id", viewer.getValue())
                    .or().apply("EXISTS (SELECT 1 FROM crm_work_task_series_follower visible_follower"
                                    + " WHERE visible_follower.tenant_id = crm_work_task_series.tenant_id"
                                    + " AND visible_follower.work_task_series_id = crm_work_task_series.id"
                                    + " AND visible_follower.follower_actor_id = {0})", viewer.getValue()));
        }
        return query;
    }

    private Optional<WorkTaskSeries> hydrate(TenantId tenantId, WorkTaskSeriesPO po) {
        if (po == null) return Optional.empty();
        return Optional.of(converter.toDomain(po, weekdays(tenantId, List.of(po.getId())),
                followerIds(tenantId, List.of(po.getId())), reminderSteps(tenantId, List.of(po.getId()))));
    }

    private List<WorkTaskSeries> hydrate(TenantId tenantId, Collection<WorkTaskSeriesPO> roots) {
        if (roots == null || roots.isEmpty()) return List.of();
        List<Long> ids = roots.stream().map(WorkTaskSeriesPO::getId).toList();
        Map<Long, List<Integer>> weekdays = weekdayRows(tenantId, ids).stream().collect(Collectors.groupingBy(
                WorkTaskSeriesWeekdayPO::getWorkTaskSeriesId, Collectors.mapping(
                        WorkTaskSeriesWeekdayPO::getWeekday, Collectors.toList())));
        Map<Long, List<Long>> followers = followerRows(tenantId, ids).stream().collect(Collectors.groupingBy(
                WorkTaskSeriesFollowerPO::getWorkTaskSeriesId, Collectors.mapping(
                        WorkTaskSeriesFollowerPO::getFollowerActorId, Collectors.toList())));
        Map<Long, List<io.github.archetom.crm.domain.model.WorkTaskReminderStep>> reminders =
                reminderRows(tenantId, ids).stream().collect(Collectors.groupingBy(
                        WorkTaskSeriesReminderStepPO::getWorkTaskSeriesId,
                        Collectors.mapping(this::reminderStep, Collectors.toList())));
        return roots.stream().map(root -> converter.toDomain(root,
                weekdays.getOrDefault(root.getId(), List.of()), followers.getOrDefault(root.getId(), List.of()),
                reminders.getOrDefault(root.getId(), List.of()))).toList();
    }

    private List<Integer> weekdays(TenantId tenantId, List<Long> ids) {
        return weekdayRows(tenantId, ids).stream().map(WorkTaskSeriesWeekdayPO::getWeekday).toList();
    }
    private List<Long> followerIds(TenantId tenantId, List<Long> ids) {
        return followerRows(tenantId, ids).stream().map(WorkTaskSeriesFollowerPO::getFollowerActorId).toList();
    }
    private List<WorkTaskSeriesWeekdayPO> weekdayRows(TenantId tenantId, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return weekdayMapper.selectList(new LambdaQueryWrapper<WorkTaskSeriesWeekdayPO>()
                .eq(WorkTaskSeriesWeekdayPO::getTenantId, tenantId.getValue())
                .in(WorkTaskSeriesWeekdayPO::getWorkTaskSeriesId, ids));
    }
    private List<WorkTaskSeriesFollowerPO> followerRows(TenantId tenantId, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return followerMapper.selectList(new LambdaQueryWrapper<WorkTaskSeriesFollowerPO>()
                .eq(WorkTaskSeriesFollowerPO::getTenantId, tenantId.getValue())
                .in(WorkTaskSeriesFollowerPO::getWorkTaskSeriesId, ids));
    }
    private List<io.github.archetom.crm.domain.model.WorkTaskReminderStep> reminderSteps(
            TenantId tenantId, Collection<Long> ids) {
        return reminderRows(tenantId, ids).stream().map(this::reminderStep).toList();
    }
    private List<WorkTaskSeriesReminderStepPO> reminderRows(TenantId tenantId, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return reminderStepMapper.selectList(new LambdaQueryWrapper<WorkTaskSeriesReminderStepPO>()
                .eq(WorkTaskSeriesReminderStepPO::getTenantId, tenantId.getValue())
                .in(WorkTaskSeriesReminderStepPO::getWorkTaskSeriesId, ids)
                .orderByAsc(WorkTaskSeriesReminderStepPO::getStepKey));
    }
    private io.github.archetom.crm.domain.model.WorkTaskReminderStep reminderStep(
            WorkTaskSeriesReminderStepPO row) {
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

    private void synchronizeWeekdays(TenantId tenantId, long seriesId, WorkTaskSeries series) {
        weekdayMapper.delete(new LambdaQueryWrapper<WorkTaskSeriesWeekdayPO>()
                .eq(WorkTaskSeriesWeekdayPO::getTenantId, tenantId.getValue())
                .eq(WorkTaskSeriesWeekdayPO::getWorkTaskSeriesId, seriesId));
        for (var day : series.getRecurrenceRule().weekdays()) {
            WorkTaskSeriesWeekdayPO po = new WorkTaskSeriesWeekdayPO();
            po.setTenantId(tenantId.getValue()); po.setWorkTaskSeriesId(seriesId); po.setWeekday(day.getValue());
            if (weekdayMapper.insert(po) != 1) throw new IllegalStateException("work task series weekday insert failed");
        }
    }
    private void synchronizeFollowers(TenantId tenantId, long seriesId, WorkTaskSeries series) {
        followerMapper.delete(new LambdaQueryWrapper<WorkTaskSeriesFollowerPO>()
                .eq(WorkTaskSeriesFollowerPO::getTenantId, tenantId.getValue())
                .eq(WorkTaskSeriesFollowerPO::getWorkTaskSeriesId, seriesId));
        for (MemberId follower : series.getFollowerMemberIds()) {
            WorkTaskSeriesFollowerPO po = new WorkTaskSeriesFollowerPO();
            po.setTenantId(tenantId.getValue()); po.setWorkTaskSeriesId(seriesId);
            po.setFollowerActorId(follower.getValue());
            if (followerMapper.insert(po) != 1) throw new IllegalStateException("work task series follower insert failed");
        }
    }
    private void synchronizeReminderSteps(TenantId tenantId, long seriesId, WorkTaskSeries series) {
        reminderStepMapper.delete(new LambdaQueryWrapper<WorkTaskSeriesReminderStepPO>()
                .eq(WorkTaskSeriesReminderStepPO::getTenantId, tenantId.getValue())
                .eq(WorkTaskSeriesReminderStepPO::getWorkTaskSeriesId, seriesId));
        for (var step : series.getReminderPlan().steps()) {
            WorkTaskSeriesReminderStepPO row = new WorkTaskSeriesReminderStepPO();
            row.setTenantId(tenantId.getValue()); row.setWorkTaskSeriesId(seriesId);
            row.setStepKey(step.stepKey()); row.setTiming(step.timing().name());
            row.setOffsetMinutes(step.offsetMinutes());
            row.setNotifyAssignee(step.recipients().contains(
                    io.github.archetom.crm.domain.model.WorkTaskReminderRecipient.ASSIGNEE));
            row.setNotifyCreator(step.recipients().contains(
                    io.github.archetom.crm.domain.model.WorkTaskReminderRecipient.CREATOR));
            row.setNotifyFollowers(step.recipients().contains(
                    io.github.archetom.crm.domain.model.WorkTaskReminderRecipient.FOLLOWERS));
            if (reminderStepMapper.insert(row) != 1) throw new IllegalStateException(
                    "work task series reminder step insert failed");
        }
    }
    private static void requireTenant(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("work task series tenant is required");
    }
}
