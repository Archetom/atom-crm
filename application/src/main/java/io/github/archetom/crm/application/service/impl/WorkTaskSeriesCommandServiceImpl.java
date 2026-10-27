package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesCreateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesVersionRequest;
import io.github.archetom.crm.application.automation.AutomationKind;
import io.github.archetom.crm.application.automation.AutomationScheduleManager;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.automation.AutomationScheduleStore;
import io.github.archetom.crm.application.port.out.automation.WorkRecurrenceControlStore;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.properties.WorkRecurrenceProperties;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.application.service.WorkTaskSeriesCommandService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.WorkTaskSeriesVO;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.WorkTaskSeries;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.WorkTaskSeriesRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkTaskSeriesCommandServiceImpl implements WorkTaskSeriesCommandService {
    private final WorkTaskSeriesRepository repository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationTenantRepository tenantRepository;
    private final OrganizationAccessGuard accessGuard;
    private final TenantAuthorizationEvaluator authorizationEvaluator;
    private final AutomationScheduleManager scheduleManager;
    private final AutomationScheduleStore scheduleStore;
    private final SchedulingProperties schedulingProperties;
    private final WorkRecurrenceProperties recurrenceProperties;
    private final WorkRecurrenceControlStore recurrenceControl;
    private final WorkTaskSeriesApplicationSupport support;
    private final CommandServiceTemplate commandTemplate;

    @Override
    public Result<WorkTaskSeriesVO> create(AuthenticatedCaller caller, WorkTaskSeriesCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.WORK_TASK_SERIES_CREATE, new ServiceOperation<>() {
            private TenantId tenantId; private MemberId creator; private MemberId assignee;
            private java.util.List<MemberId> followers; private LocalDateTime now;
            @Override public void validate() {
                if (request == null) throw support.invalid("Work task series request is required");
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.WORK_TASKS_WRITE);
                creator = support.memberId(caller.actorId()); assignee = support.memberId(request.getAssigneeActorId());
                followers = support.followerIds(request.getFollowerActorIds()); support.rule(request);
                support.priority(request.getPriority(), true);
            }
            @Override public WorkTaskSeriesVO execute() {
                lockTenant(tenantId);
                now = databaseNow();
                if (repository.countLive(tenantId) >= 1_000) throw support.invalid("Work task series capacity is exhausted");
                requireActive(tenantId, creator, "creator"); requireActive(tenantId, assignee, "assignee");
                followers.forEach(member -> requireActive(tenantId, member, "follower"));
                WorkTaskSeries saved = repository.save(tenantId, WorkTaskSeries.create(tenantId, creator, assignee,
                        followers, request.getTitle(), request.getDescription(), support.priority(request.getPriority(), true),
                        support.rule(request), now));
                return support.toVO(saved);
            }
        });
    }

    @Override
    public Result<WorkTaskSeriesVO> update(AuthenticatedCaller caller, Long seriesIdValue, WorkTaskSeriesUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.WORK_TASK_SERIES_UPDATE, new ServiceOperation<>() {
            private TenantId tenantId; private MemberId actor; private MemberId assignee;
            private java.util.List<MemberId> followers; private io.github.archetom.crm.domain.valueobject.WorkTaskSeriesId seriesId; private LocalDateTime now;
            @Override public void validate() {
                if (request == null) throw support.invalid("Work task series request is required");
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.WORK_TASKS_WRITE);
                actor = support.memberId(caller.actorId()); seriesId = support.seriesId(seriesIdValue);
                assignee = support.memberId(request.getAssigneeActorId()); followers = support.followerIds(request.getFollowerActorIds());
                support.rule(request); support.priority(request.getPriority(), false);
            }
            @Override public WorkTaskSeriesVO execute() {
                lockTenant(tenantId);
                WorkTaskSeries preflight = visible(caller, tenantId, actor, seriesId);
                if (preflight.getStatus() == io.github.archetom.crm.domain.model.WorkTaskSeriesStatus.ACTIVE) {
                    recurrenceControl.requireCurrent(recurrenceProperties.policy());
                }
                prepareRecurrenceSchedule(tenantId);
                WorkTaskSeries series = visibleForUpdate(caller, tenantId, actor, seriesId);
                now = databaseNow();
                requireManager(caller, series, actor); support.requireVersion(series, request.getVersion());
                requireActive(tenantId, assignee, "assignee"); followers.forEach(member -> requireActive(tenantId, member, "follower"));
                series.updateFuture(assignee, followers, request.getTitle(), request.getDescription(),
                        support.priority(request.getPriority(), false), support.rule(request), now);
                WorkTaskSeries saved = repository.save(tenantId, series);
                synchronizeRecurrenceSchedule(tenantId);
                return support.toVO(saved);
            }
        });
    }

    @Override
    public Result<WorkTaskSeriesVO> activate(AuthenticatedCaller caller, Long seriesIdValue, WorkTaskSeriesVersionRequest request) {
        return state(caller, seriesIdValue, request, true);
    }

    @Override
    public Result<WorkTaskSeriesVO> pause(AuthenticatedCaller caller, Long seriesIdValue, WorkTaskSeriesVersionRequest request) {
        return state(caller, seriesIdValue, request, false);
    }

    @Override
    public Result<WorkTaskSeriesVO> delete(AuthenticatedCaller caller, Long seriesIdValue, Long version) {
        return commandTemplate.execute(UseCaseOperation.WORK_TASK_SERIES_DELETE, new ServiceOperation<>() {
            private TenantId tenantId; private MemberId actor; private io.github.archetom.crm.domain.valueobject.WorkTaskSeriesId seriesId; private LocalDateTime now;
            @Override public void validate() { tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.WORK_TASKS_WRITE);
                actor = support.memberId(caller.actorId()); seriesId = support.seriesId(seriesIdValue); }
            @Override public WorkTaskSeriesVO execute() {
                lockTenant(tenantId); prepareRecurrenceSchedule(tenantId);
                WorkTaskSeries series = visibleForUpdate(caller, tenantId, actor, seriesId);
                now = databaseNow();
                requireManager(caller, series, actor); support.requireVersion(series, version); series.delete(now);
                WorkTaskSeries saved = repository.save(tenantId, series);
                synchronizeRecurrenceSchedule(tenantId);
                return support.toVO(saved);
            }
        });
    }

    private Result<WorkTaskSeriesVO> state(AuthenticatedCaller caller, Long seriesIdValue,
                                            WorkTaskSeriesVersionRequest request, boolean activate) {
        return commandTemplate.execute(activate ? UseCaseOperation.WORK_TASK_SERIES_ACTIVATE : UseCaseOperation.WORK_TASK_SERIES_PAUSE,
                new ServiceOperation<>() {
                    private TenantId tenantId; private MemberId actor;
                    private io.github.archetom.crm.domain.valueobject.WorkTaskSeriesId seriesId; private LocalDateTime now;
                    @Override public void validate() {
                        if (request == null) throw support.invalid("Work task series version is required");
                        tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.WORK_TASKS_WRITE);
                        actor = support.memberId(caller.actorId()); seriesId = support.seriesId(seriesIdValue);
                        if (activate && (!schedulingProperties.policy().enabled() || !schedulingProperties.policy().valid()
                                || !schedulingProperties.recurrenceValid()
                                || scheduleStore.schedulingReadiness(schedulingProperties.policy())
                                != io.github.archetom.crm.application.automation.AutomationSchedulingReadiness.READY
                                || recurrenceControl.readiness(recurrenceProperties.policy())
                                != io.github.archetom.crm.application.automation.WorkRecurrenceReadiness.READY)) {
                            throw support.invalid("Work recurrence scheduling is not ready");
                        }
                    }
                    @Override public WorkTaskSeriesVO execute() {
                        lockTenant(tenantId);
                        if (activate) recurrenceControl.requireCurrent(recurrenceProperties.policy());
                        prepareRecurrenceSchedule(tenantId);
                        WorkTaskSeries series = visibleForUpdate(caller, tenantId, actor, seriesId);
                        now = databaseNow();
                        requireManager(caller, series, actor); support.requireVersion(series, request.getVersion());
                        if (activate) {
                            requireActivationAuthorization(tenantId, series);
                            series.activate(now);
                        } else series.pause(now, "PAUSED");
                        WorkTaskSeries saved = repository.save(tenantId, series);
                        synchronizeRecurrenceSchedule(tenantId);
                        return support.toVO(saved);
                    }
                });
    }

    private WorkTaskSeries visibleForUpdate(AuthenticatedCaller caller, TenantId tenantId, MemberId actor,
                                            io.github.archetom.crm.domain.valueobject.WorkTaskSeriesId seriesId) {
        return repository.findVisibleByIdForUpdate(tenantId, seriesId, actor,
                caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE)).orElseThrow(
                () -> support.notFound("Work task series was not found"));
    }
    private WorkTaskSeries visible(AuthenticatedCaller caller, TenantId tenantId, MemberId actor,
                                   io.github.archetom.crm.domain.valueobject.WorkTaskSeriesId seriesId) {
        return repository.findVisibleById(tenantId, seriesId, actor,
                caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE)).orElseThrow(
                () -> support.notFound("Work task series was not found"));
    }
    private void requireManager(AuthenticatedCaller caller, WorkTaskSeries series, MemberId actor) {
        if (!series.isCreatedBy(actor) && !caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE))
            throw support.denied("Only the work task series creator or a task manager can manage the series");
    }
    private void lockTenant(TenantId tenantId) {
        if (!tenantRepository.lockForTenantChanges(tenantId)) throw support.notFound("Work task series tenant was not found");
    }
    private void requireActive(TenantId tenantId, MemberId memberId, String role) {
        OrganizationMembership membership = membershipRepository.findByMember(tenantId, memberId)
                .orElseThrow(() -> support.invalid("Work task series " + role + " is invalid"));
        if (!membership.isActive()) throw support.invalid("Work task series " + role + " is not active");
    }
    private void prepareRecurrenceSchedule(TenantId tenantId) {
        scheduleManager.preparePoolConfiguration(tenantId, AutomationKind.WORK_TASK_RECURRENCE, true);
    }
    private void synchronizeRecurrenceSchedule(TenantId tenantId) {
        scheduleManager.synchronize(tenantId, AutomationKind.WORK_TASK_RECURRENCE, repository.hasActive(tenantId));
    }
    private void requireActivationAuthorization(TenantId tenantId, WorkTaskSeries series) {
        Map<MemberId, TenantAuthorizationEvaluator.Evaluation> authorization = authorizationEvaluator.evaluateAll(
                tenantId, participants(series));
        if (!canCreate(authorization.get(series.getCreatedByMemberId()))
                || !canCreate(authorization.get(series.getCurrentAssigneeMemberId()))) {
            throw support.invalid("Work task series creator or assignee is not eligible for activation");
        }
        if (series.getFollowerMemberIds().stream().anyMatch(member -> !canRead(authorization.get(member)))) {
            throw support.invalid("Work task series follower is not eligible for activation");
        }
    }
    private static Collection<MemberId> participants(WorkTaskSeries series) {
        java.util.LinkedHashSet<MemberId> participants = new java.util.LinkedHashSet<>();
        participants.add(series.getCreatedByMemberId());
        participants.add(series.getCurrentAssigneeMemberId());
        participants.addAll(series.getFollowerMemberIds());
        return participants;
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
    private LocalDateTime databaseNow() { return scheduleStore.currentUtc(); }
}
