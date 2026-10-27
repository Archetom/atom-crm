package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.automation.AutomationSchedulingReadiness;
import io.github.archetom.crm.application.automation.WorkRecurrenceReadiness;
import io.github.archetom.crm.application.port.out.automation.AutomationScheduleStore;
import io.github.archetom.crm.application.port.out.automation.WorkRecurrenceControlStore;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.properties.WorkRecurrenceProperties;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.application.service.WorkTaskSeriesCapabilityService;
import io.github.archetom.crm.application.vo.WorkTaskSeriesCapabilityVO;
import io.github.archetom.crm.application.vo.WorkTaskSeriesVO;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Keeps browser interaction eligibility in the application layer, close to the write revalidation rules. */
@Service
@RequiredArgsConstructor
public class WorkTaskSeriesCapabilityServiceImpl implements WorkTaskSeriesCapabilityService {
    private final TenantAuthorizationEvaluator authorizationEvaluator;
    private final AutomationScheduleStore scheduleStore;
    private final SchedulingProperties schedulingProperties;
    private final WorkRecurrenceControlStore recurrenceControl;
    private final WorkRecurrenceProperties recurrenceProperties;

    @Override
    public WorkTaskSeriesCapabilityVO resolve(AuthenticatedCaller caller, WorkTaskSeriesVO series) {
        if (!canManage(caller, series)) {
            return WorkTaskSeriesCapabilityVO.none();
        }
        boolean schedulerReady = schedulerReady();
        boolean participantEligible = activationParticipantsEligible(caller, series);
        List<String> blockers = blockers(series.status(), schedulerReady, participantEligible);
        List<String> actions = new ArrayList<>();
        if (canUpdate(series.status(), schedulerReady)) {
            actions.add("UPDATE");
        }
        if (canActivate(series.status(), schedulerReady, participantEligible)) {
            actions.add("ACTIVATE");
        }
        if ("ACTIVE".equals(series.status())) {
            actions.add("PAUSE");
        }
        if (!"DELETED".equals(series.status())) {
            actions.add("STOP");
        }
        return new WorkTaskSeriesCapabilityVO(actions, blockers);
    }

    private boolean canManage(AuthenticatedCaller caller, WorkTaskSeriesVO series) {
        return caller != null && caller.hasAuthority(AuthorityCatalog.WORK_TASKS_WRITE)
                && (caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE)
                || series.createdByActorId() != null && series.createdByActorId().equals(caller.actorId()));
    }

    private boolean schedulerReady() {
        var schedulingPolicy = schedulingProperties.policy();
        return schedulingPolicy.enabled() && schedulingPolicy.valid() && schedulingProperties.recurrenceValid()
                && scheduleStore.schedulingReadiness(schedulingPolicy) == AutomationSchedulingReadiness.READY
                && recurrenceControl.readiness(recurrenceProperties.policy()) == WorkRecurrenceReadiness.READY;
    }

    private boolean activationParticipantsEligible(AuthenticatedCaller caller, WorkTaskSeriesVO series) {
        if (series.createdByActorId() == null || series.currentAssigneeActorId() == null) {
            return false;
        }
        MemberId creator = new MemberId(series.createdByActorId());
        MemberId assignee = new MemberId(series.currentAssigneeActorId());
        Set<MemberId> participants = new LinkedHashSet<>();
        participants.add(creator);
        participants.add(assignee);
        List<MemberId> followers = series.followerActorIds() == null ? List.of()
                : series.followerActorIds().stream().map(MemberId::new).toList();
        participants.addAll(followers);
        Map<MemberId, TenantAuthorizationEvaluator.Evaluation> evaluations = authorizationEvaluator.evaluateAll(
                new TenantId(caller.tenantId()), participants);
        return canCreate(evaluations.get(creator)) && canCreate(evaluations.get(assignee))
                && followers.stream().allMatch(member -> canRead(evaluations.get(member)));
    }

    private static boolean canUpdate(String status, boolean schedulerReady) {
        return "DRAFT".equals(status) || "PAUSED".equals(status)
                || "ACTIVE".equals(status) && schedulerReady;
    }

    private static boolean canActivate(String status, boolean schedulerReady, boolean participantEligible) {
        return ("DRAFT".equals(status) || "PAUSED".equals(status)) && schedulerReady && participantEligible;
    }

    private static List<String> blockers(String status, boolean schedulerReady, boolean participantEligible) {
        if (!("DRAFT".equals(status) || "PAUSED".equals(status) || "ACTIVE".equals(status))) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (!schedulerReady) {
            result.add("SCHEDULER_UNAVAILABLE");
        }
        if (("DRAFT".equals(status) || "PAUSED".equals(status)) && !participantEligible) {
            result.add("PARTICIPANT_INELIGIBLE");
        }
        return List.copyOf(result);
    }

    private static boolean canCreate(TenantAuthorizationEvaluator.Evaluation evaluation) {
        return canRead(evaluation) && has(evaluation, AuthorityCatalog.WORK_TASKS_WRITE);
    }

    private static boolean canRead(TenantAuthorizationEvaluator.Evaluation evaluation) {
        return evaluation != null && evaluation.activeMember() && has(evaluation, AuthorityCatalog.WORK_TASKS_READ);
    }

    private static boolean has(TenantAuthorizationEvaluator.Evaluation evaluation, String authority) {
        return evaluation != null && evaluation.effectiveGrants().stream()
                .anyMatch(grant -> authority.equals(grant.authorityCode().getValue()));
    }
}
