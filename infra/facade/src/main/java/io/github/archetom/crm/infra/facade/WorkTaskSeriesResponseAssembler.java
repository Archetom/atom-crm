package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.dto.response.WorkTaskSeriesOccurrenceResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesDetailResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesListItemResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesMutationResultResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesRecurrenceResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesActionBlockerResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesActionResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesStatusResponse;
import io.github.archetom.crm.application.vo.WorkTaskSeriesCapabilityVO;
import io.github.archetom.crm.application.vo.WorkTaskSeriesOccurrenceVO;
import io.github.archetom.crm.application.vo.WorkTaskSeriesVO;

final class WorkTaskSeriesResponseAssembler {
    private WorkTaskSeriesResponseAssembler() { }
    static WorkTaskSeriesListItemResponse toListItem(WorkTaskSeriesVO source,
                                                      WorkTaskSeriesCapabilityVO capability) {
        return new WorkTaskSeriesListItemResponse(source.id(), source.version(), source.title(), status(source),
                recurrence(source), actions(capability), blockers(capability));
    }
    static WorkTaskSeriesDetailResponse toDetail(WorkTaskSeriesVO source,
                                                  WorkTaskSeriesCapabilityVO capability) {
        return new WorkTaskSeriesDetailResponse(source.id(), source.version(), source.title(), source.description(),
                source.priority(), source.currentAssigneeActorId(), source.followerActorIds(), status(source),
                recurrence(source), source.nextNominalLocalTime(), source.nextDueTime(),
                actions(capability), blockers(capability));
    }
    static WorkTaskSeriesMutationResultResponse toMutation(WorkTaskSeriesVO source,
                                                            WorkTaskSeriesCapabilityVO capability) {
        return new WorkTaskSeriesMutationResultResponse(source.id(), source.version(), status(source),
                actions(capability));
    }
    private static WorkTaskSeriesRecurrenceResponse recurrence(WorkTaskSeriesVO source) {
        return new WorkTaskSeriesRecurrenceResponse(source.recurrenceFrequency(), source.recurrenceInterval(),
                source.weekdays(), source.monthDay(), source.lastDayOfMonth(), source.zoneId(),
                source.startLocalDate(), source.localTime(), source.endLocalDate());
    }
    private static WorkTaskSeriesStatusResponse status(WorkTaskSeriesVO source) {
        return switch (source.status()) {
            case "DRAFT" -> WorkTaskSeriesStatusResponse.DRAFT;
            case "ACTIVE" -> WorkTaskSeriesStatusResponse.ACTIVE;
            case "PAUSED" -> WorkTaskSeriesStatusResponse.PAUSED;
            case "DELETED" -> WorkTaskSeriesStatusResponse.STOPPED;
            case "ENDED" -> WorkTaskSeriesStatusResponse.ENDED;
            default -> throw new IllegalStateException("unexpected Work task series status");
        };
    }
    private static java.util.List<WorkTaskSeriesActionResponse> actions(WorkTaskSeriesCapabilityVO capability) {
        return capability.allowedActions().stream().map(value -> switch (value) {
            case "UPDATE" -> WorkTaskSeriesActionResponse.UPDATE;
            case "ACTIVATE" -> WorkTaskSeriesActionResponse.ACTIVATE;
            case "PAUSE" -> WorkTaskSeriesActionResponse.PAUSE;
            case "STOP" -> WorkTaskSeriesActionResponse.STOP;
            default -> throw new IllegalStateException("unexpected Work task series action");
        }).toList();
    }
    private static java.util.List<WorkTaskSeriesActionBlockerResponse> blockers(WorkTaskSeriesCapabilityVO capability) {
        return capability.actionBlockers().stream().map(value -> switch (value) {
            case "SCHEDULER_UNAVAILABLE" -> WorkTaskSeriesActionBlockerResponse.SCHEDULER_UNAVAILABLE;
            case "PARTICIPANT_INELIGIBLE" -> WorkTaskSeriesActionBlockerResponse.PARTICIPANT_INELIGIBLE;
            default -> throw new IllegalStateException("unexpected Work task series action blocker");
        }).toList();
    }
    static WorkTaskSeriesOccurrenceResponse toResponse(WorkTaskSeriesOccurrenceVO source) {
        WorkTaskSeriesOccurrenceResponse target = new WorkTaskSeriesOccurrenceResponse();
        target.setId(source.id()); target.setSeriesId(source.seriesId()); target.setSeriesGeneration(source.seriesGeneration());
        target.setOccurrenceSequence(source.occurrenceSequence()); target.setStatus(source.status());
        target.setNominalLocalTime(source.nominalLocalTime()); target.setZoneId(source.zoneId());
        target.setResolvedOffsetSeconds(source.resolvedOffsetSeconds()); target.setResolvedDueTime(source.resolvedDueTime());
        target.setGeneratedWorkTaskId(source.generatedWorkTaskId()); target.setSafeReason(source.safeReason());
        target.setCreatedTime(source.createdTime()); return target;
    }
}
