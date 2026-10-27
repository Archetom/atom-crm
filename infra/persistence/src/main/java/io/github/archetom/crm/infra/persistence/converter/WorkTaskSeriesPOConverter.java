package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.WorkTaskSeries;
import io.github.archetom.crm.domain.entity.WorkTaskSeriesOccurrence;
import io.github.archetom.crm.domain.model.WorkRecurrenceFrequency;
import io.github.archetom.crm.domain.model.WorkRecurrenceRule;
import io.github.archetom.crm.domain.model.WorkTaskPriority;
import io.github.archetom.crm.domain.model.WorkTaskSeriesOccurrenceStatus;
import io.github.archetom.crm.domain.model.WorkTaskSeriesStatus;
import io.github.archetom.crm.domain.model.WorkTaskReminderPlan;
import io.github.archetom.crm.domain.model.WorkTaskReminderStep;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import io.github.archetom.crm.domain.valueobject.WorkTaskSeriesId;
import io.github.archetom.crm.domain.valueobject.WorkTaskSeriesOccurrenceId;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskSeriesOccurrencePO;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskSeriesPO;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.Set;

@Component
public class WorkTaskSeriesPOConverter {
    public WorkTaskSeriesPO toPO(WorkTaskSeries series) {
        WorkTaskSeriesPO po = new WorkTaskSeriesPO();
        po.setId(series.getId() == null ? null : series.getId().getValue());
        po.setTenantId(series.getTenantId().getValue());
        po.setCreatedByActorId(series.getCreatedByMemberId().getValue());
        po.setCurrentAssigneeActorId(series.getCurrentAssigneeMemberId().getValue());
        po.setTitle(series.getTitle());
        po.setDescription(series.getDescription());
        po.setPriority(series.getPriority().name());
        WorkRecurrenceRule rule = series.getRecurrenceRule();
        po.setRecurrenceFrequency(rule.frequency().name());
        po.setRecurrenceInterval(rule.interval());
        po.setMonthDay(rule.monthDay());
        po.setLastDayOfMonth(rule.lastDayOfMonth());
        po.setZoneId(rule.zoneId());
        po.setStartLocalDate(rule.startDate());
        po.setLocalTime(rule.localTime());
        po.setEndLocalDate(rule.endDate());
        po.setStatus(series.getStatus().name());
        po.setGeneration(series.getGeneration());
        po.setNextOccurrenceSequence(series.getNextOccurrenceSequence());
        po.setNextNominalLocalTime(series.getNextNominalLocalTime());
        po.setNextDueAt(series.getNextDueTime());
        po.setOccurrenceCount(series.getOccurrenceCount());
        po.setSkippedCount(series.getSkippedCount());
        po.setLastSafeReason(series.getLastSafeReason());
        po.setVersion(series.getVersion());
        po.setCreatedTime(series.getCreatedTime());
        po.setUpdatedTime(series.getUpdatedTime());
        return po;
    }

    public WorkTaskSeries toDomain(WorkTaskSeriesPO po, Collection<Integer> weekdays,
                                   Collection<Long> followerActorIds) {
        return toDomain(po, weekdays, followerActorIds, java.util.List.of());
    }

    public WorkTaskSeries toDomain(WorkTaskSeriesPO po, Collection<Integer> weekdays,
                                   Collection<Long> followerActorIds,
                                   Collection<WorkTaskReminderStep> reminderSteps) {
        if (po == null) return null;
        Set<DayOfWeek> days = weekdays == null ? Set.of() : weekdays.stream().map(DayOfWeek::of).collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<MemberId> followers = followerActorIds == null ? Set.of() : followerActorIds.stream()
                .map(MemberId::new).collect(java.util.stream.Collectors.toUnmodifiableSet());
        WorkRecurrenceRule rule = new WorkRecurrenceRule(
                WorkRecurrenceFrequency.valueOf(po.getRecurrenceFrequency()), po.getRecurrenceInterval(), days,
                po.getMonthDay(), Boolean.TRUE.equals(po.getLastDayOfMonth()), po.getZoneId(),
                po.getStartLocalDate(), po.getLocalTime(), po.getEndLocalDate());
        return WorkTaskSeries.reconstitute(new WorkTaskSeries.Snapshot(
                new WorkTaskSeriesId(po.getId()), new TenantId(po.getTenantId()),
                new MemberId(po.getCreatedByActorId()), new MemberId(po.getCurrentAssigneeActorId()), followers,
                po.getTitle(), po.getDescription(), WorkTaskPriority.valueOf(po.getPriority()),
                WorkTaskReminderPlan.of(reminderSteps), rule,
                WorkTaskSeriesStatus.valueOf(po.getStatus()), po.getGeneration(), po.getNextOccurrenceSequence(),
                po.getNextNominalLocalTime(), po.getNextDueAt(), po.getOccurrenceCount(), po.getSkippedCount(),
                po.getLastSafeReason(), po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }

    public WorkTaskSeriesOccurrencePO toPO(WorkTaskSeriesOccurrence occurrence) {
        WorkTaskSeriesOccurrencePO po = new WorkTaskSeriesOccurrencePO();
        po.setId(occurrence.id() == null ? null : occurrence.id().getValue());
        po.setTenantId(occurrence.tenantId().getValue());
        po.setWorkTaskSeriesId(occurrence.seriesId().getValue());
        po.setSeriesGeneration(occurrence.seriesGeneration());
        po.setOccurrenceSequence(occurrence.occurrenceSequence());
        po.setStatus(occurrence.status().name());
        po.setNominalLocalTime(occurrence.nominalLocalTime());
        po.setZoneId(occurrence.zoneId());
        po.setResolvedOffsetSeconds(occurrence.resolvedOffsetSeconds());
        po.setResolvedDueTime(occurrence.resolvedDueTime());
        po.setGeneratedWorkTaskId(occurrence.generatedTaskId() == null ? null : occurrence.generatedTaskId().getValue());
        po.setSafeReason(occurrence.safeReason());
        po.setCreatedTime(occurrence.createdTime());
        return po;
    }

    public WorkTaskSeriesOccurrence toDomain(WorkTaskSeriesOccurrencePO po) {
        if (po == null) return null;
        return new WorkTaskSeriesOccurrence(new WorkTaskSeriesOccurrenceId(po.getId()), new TenantId(po.getTenantId()),
                new WorkTaskSeriesId(po.getWorkTaskSeriesId()), po.getSeriesGeneration(), po.getOccurrenceSequence(),
                WorkTaskSeriesOccurrenceStatus.valueOf(po.getStatus()), po.getNominalLocalTime(), po.getZoneId(),
                po.getResolvedOffsetSeconds(), po.getResolvedDueTime(), po.getGeneratedWorkTaskId() == null ? null
                : new WorkTaskId(po.getGeneratedWorkTaskId()), po.getSafeReason(), po.getCreatedTime());
    }
}
