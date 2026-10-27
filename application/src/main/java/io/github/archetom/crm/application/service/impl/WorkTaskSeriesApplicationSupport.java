package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.dto.request.WorkTaskSeriesCreateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesUpdateRequest;
import io.github.archetom.crm.application.vo.WorkTaskSeriesOccurrenceVO;
import io.github.archetom.crm.application.vo.WorkTaskSeriesVO;
import io.github.archetom.crm.domain.entity.WorkTaskSeries;
import io.github.archetom.crm.domain.entity.WorkTaskSeriesOccurrence;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.model.WorkRecurrenceFrequency;
import io.github.archetom.crm.domain.model.WorkRecurrenceRule;
import io.github.archetom.crm.domain.model.WorkTaskPriority;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.WorkTaskSeriesId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Component
class WorkTaskSeriesApplicationSupport {
    WorkTaskSeriesId seriesId(Long value) {
        try { return new WorkTaskSeriesId(value); }
        catch (RuntimeException exception) { throw invalid("Work task series ID is invalid"); }
    }

    MemberId memberId(Long value) {
        try { return new MemberId(value); }
        catch (RuntimeException exception) { throw invalid("Work task series member is invalid"); }
    }

    List<MemberId> followerIds(Collection<Long> values) {
        if (values == null || values.isEmpty()) return List.of();
        if (values.size() > WorkTaskSeries.MAX_FOLLOWERS) throw invalid("Work task series followers are invalid");
        LinkedHashSet<Long> result = new LinkedHashSet<>(values);
        if (result.size() != values.size() || result.stream().anyMatch(v -> v == null || v < 1)) {
            throw invalid("Work task series followers must be unique active tenant members");
        }
        return result.stream().map(MemberId::new).toList();
    }

    WorkTaskPriority priority(String value, boolean defaultNormal) {
        if (defaultNormal && (value == null || value.isBlank())) return WorkTaskPriority.NORMAL;
        try { return WorkTaskPriority.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw invalid("Work task series priority is invalid"); }
    }

    WorkRecurrenceRule rule(WorkTaskSeriesCreateRequest request) {
        if (request == null) throw invalid("Work task series request is required");
        return rule(request.getRecurrenceFrequency(), request.getRecurrenceInterval(), request.getWeekdays(),
                request.getMonthDay(), request.getLastDayOfMonth(), request.getZoneId(),
                request.getStartLocalDate(), request.getLocalTime(), request.getEndLocalDate());
    }

    WorkRecurrenceRule rule(WorkTaskSeriesUpdateRequest request) {
        if (request == null) throw invalid("Work task series request is required");
        return rule(request.getRecurrenceFrequency(), request.getRecurrenceInterval(), request.getWeekdays(),
                request.getMonthDay(), request.getLastDayOfMonth(), request.getZoneId(),
                request.getStartLocalDate(), request.getLocalTime(), request.getEndLocalDate());
    }

    WorkTaskSeriesVO toVO(WorkTaskSeries series) {
        WorkRecurrenceRule rule = series.getRecurrenceRule();
        return new WorkTaskSeriesVO(series.getId().getValue(), series.getCreatedByMemberId().getValue(),
                series.getCurrentAssigneeMemberId().getValue(), series.getFollowerMemberIds().stream()
                .map(MemberId::getValue).sorted().toList(), series.getTitle(), series.getDescription(),
                series.getPriority().name(), rule.frequency().name(), rule.interval(), rule.weekdays().stream()
                .sorted().map(Enum::name).toList(), rule.monthDay(), rule.lastDayOfMonth(), rule.zoneId(),
                rule.startDate(), rule.localTime(), rule.endDate(), series.getStatus().name(), series.getGeneration(),
                series.getNextOccurrenceSequence(), series.getNextNominalLocalTime(),
                series.getNextDueTime(),
                series.getOccurrenceCount(), series.getSkippedCount(), series.getLastSafeReason(), series.getVersion(),
                series.getCreatedTime(), series.getUpdatedTime());
    }

    WorkTaskSeriesOccurrenceVO toVO(WorkTaskSeriesOccurrence value) {
        return new WorkTaskSeriesOccurrenceVO(value.id().getValue(), value.seriesId().getValue(),
                value.seriesGeneration(), value.occurrenceSequence(), value.status().name(),
                value.nominalLocalTime(), value.zoneId(), value.resolvedOffsetSeconds(), value.resolvedDueTime(),
                value.generatedTaskId() == null ? null : value.generatedTaskId().getValue(), value.safeReason(), value.createdTime());
    }

    void requireVersion(WorkTaskSeries series, Long version) {
        if (series == null || version == null || version < 0 || !version.equals(series.getVersion())) {
            throw new AggregateVersionConflictException();
        }
    }

    NonRetryableApplicationException invalid(String message) { return error(ApplicationErrorCode.PARAMETER_INVALID, message); }
    NonRetryableApplicationException denied(String message) { return error(ApplicationErrorCode.ACCESS_DENIED, message); }
    NonRetryableApplicationException notFound(String message) { return error(ApplicationErrorCode.RESOURCE_NOT_FOUND, message); }
    private NonRetryableApplicationException error(ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }

    private WorkRecurrenceRule rule(
            String frequencyValue, Integer intervalValue, Collection<String> weekdayValues,
            Integer monthDay, Boolean lastDay, String zoneId, LocalDate startDate,
            LocalTime localTime, LocalDate endDate) {
        try {
            WorkRecurrenceFrequency frequency = WorkRecurrenceFrequency.valueOf(
                    frequencyValue == null ? "" : frequencyValue.trim().toUpperCase(Locale.ROOT));
            LinkedHashSet<DayOfWeek> weekdays = new LinkedHashSet<>();
            if (weekdayValues != null) for (String value : weekdayValues) weekdays.add(DayOfWeek.valueOf(
                    value == null ? "" : value.trim().toUpperCase(Locale.ROOT)));
            return new WorkRecurrenceRule(frequency, intervalValue == null ? 0 : intervalValue,
                    weekdays, monthDay, Boolean.TRUE.equals(lastDay), zoneId, startDate, localTime, endDate);
        } catch (RuntimeException exception) {
            throw invalid("Work task recurrence rule is invalid");
        }
    }
}
