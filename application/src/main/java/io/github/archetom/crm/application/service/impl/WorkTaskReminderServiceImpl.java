package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorkTaskReminderPlanUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskReminderStepRequest;
import io.github.archetom.crm.application.automation.AutomationKind;
import io.github.archetom.crm.application.automation.AutomationScheduleManager;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.WorkTaskReminderService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.WorkTaskReminderPlanVO;
import io.github.archetom.crm.application.vo.WorkTaskReminderStepVO;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.entity.WorkTaskSeries;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.WorkTaskNotFoundException;
import io.github.archetom.crm.domain.model.WorkTaskReminderPlan;
import io.github.archetom.crm.domain.model.WorkTaskReminderRecipient;
import io.github.archetom.crm.domain.model.WorkTaskReminderStep;
import io.github.archetom.crm.domain.model.WorkTaskReminderTiming;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.WorkTaskRepository;
import io.github.archetom.crm.domain.repository.WorkTaskSeriesRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import io.github.archetom.crm.domain.valueobject.WorkTaskSeriesId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/** Human reminder-plan commands. Background effects use a separate worker and never a caller. */
@Service
@RequiredArgsConstructor
public class WorkTaskReminderServiceImpl implements WorkTaskReminderService {
    private final WorkTaskRepository taskRepository;
    private final WorkTaskSeriesRepository seriesRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationTenantRepository tenantRepository;
    private final OrganizationAccessGuard accessGuard;
    private final WorkTaskApplicationSupport taskSupport;
    private final WorkTaskReminderCoordinator coordinator;
    private final AutomationScheduleManager scheduleManager;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;
    private final Clock clock;

    @Override
    public Result<WorkTaskReminderPlanVO> getTaskPlan(AuthenticatedCaller caller, Long taskIdValue) {
        return queryTemplate.execute(UseCaseOperation.WORK_TASK_REMINDER_GET, new ServiceOperation<>() {
            private TenantId tenantId; private MemberId memberId; private WorkTaskId taskId;
            @Override public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.WORK_TASKS_READ);
                memberId = taskSupport.memberId(caller.actorId()); taskId = taskSupport.taskId(taskIdValue);
            }
            @Override public WorkTaskReminderPlanVO execute() {
                WorkTask task = taskRepository.findVisibleById(tenantId, taskId, memberId,
                                caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE))
                        .orElseThrow(WorkTaskNotFoundException::new);
                return taskPlan(task);
            }
        });
    }

    @Override
    public Result<WorkTaskReminderPlanVO> updateTaskPlan(
            AuthenticatedCaller caller, Long taskIdValue, WorkTaskReminderPlanUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.WORK_TASK_REMINDER_UPDATE, new ServiceOperation<>() {
            private TenantId tenantId; private MemberId memberId; private WorkTaskId taskId;
            private WorkTaskReminderPlan plan; private LocalDateTime now;
            @Override public void validate() {
                requireRequest(request); plan = plan(request); taskId = taskSupport.taskId(taskIdValue);
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.WORK_TASKS_WRITE);
                memberId = taskSupport.memberId(caller.actorId()); now = now();
            }
            @Override public WorkTaskReminderPlanVO execute() {
                lockTenant(tenantId); requireActive(tenantId, memberId, true);
                WorkTask task = taskRepository.findVisibleByIdForUpdate(tenantId, taskId, memberId,
                                caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE))
                        .orElseThrow(WorkTaskNotFoundException::new);
                requireTaskManager(caller, task, memberId); requireVersion(task.getVersion(), request.getVersion());
                task.replaceReminderPlan(plan, now);
                coordinator.validateForPersistence(task);
                WorkTask saved = taskRepository.save(tenantId, task);
                coordinator.synchronize(tenantId, saved, now);
                return taskPlan(saved);
            }
        });
    }

    @Override
    public Result<WorkTaskReminderPlanVO> getSeriesPlan(AuthenticatedCaller caller, Long seriesIdValue) {
        return queryTemplate.execute(UseCaseOperation.WORK_TASK_SERIES_REMINDER_GET, new ServiceOperation<>() {
            private TenantId tenantId; private MemberId memberId; private WorkTaskSeriesId seriesId;
            @Override public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.WORK_TASKS_READ);
                memberId = taskSupport.memberId(caller.actorId()); seriesId = seriesId(seriesIdValue);
            }
            @Override public WorkTaskReminderPlanVO execute() {
                WorkTaskSeries series = seriesRepository.findVisibleById(tenantId, seriesId, memberId,
                                caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE))
                        .orElseThrow(() -> taskSupport.notFound("Work task series was not found"));
                return seriesPlan(series);
            }
        });
    }

    @Override
    public Result<WorkTaskReminderPlanVO> updateSeriesPlan(
            AuthenticatedCaller caller, Long seriesIdValue, WorkTaskReminderPlanUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.WORK_TASK_SERIES_REMINDER_UPDATE, new ServiceOperation<>() {
            private TenantId tenantId; private MemberId memberId; private WorkTaskSeriesId seriesId;
            private WorkTaskReminderPlan plan; private LocalDateTime now;
            @Override public void validate() {
                requireRequest(request); plan = plan(request); seriesId = seriesId(seriesIdValue);
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.WORK_TASKS_WRITE);
                memberId = taskSupport.memberId(caller.actorId()); now = now();
            }
            @Override public WorkTaskReminderPlanVO execute() {
                lockTenant(tenantId);
                // Shares the B27a tenant -> schedule -> series order with recurrence configuration writes.
                scheduleManager.preparePoolConfiguration(tenantId, AutomationKind.WORK_TASK_RECURRENCE, true);
                requireActive(tenantId, memberId, true);
                WorkTaskSeries series = seriesRepository.findVisibleByIdForUpdate(tenantId, seriesId, memberId,
                                caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE))
                        .orElseThrow(() -> taskSupport.notFound("Work task series was not found"));
                if (!series.isCreatedBy(memberId)
                        && !caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE)) {
                    throw taskSupport.denied("Only the work task series creator or a task manager can manage the series");
                }
                requireVersion(series.getVersion(), request.getVersion());
                coordinator.validateForSeriesTemplate(series, plan);
                series.replaceReminderPlanTemplate(plan, now);
                return seriesPlan(seriesRepository.save(tenantId, series));
            }
        });
    }

    private WorkTaskReminderPlan plan(WorkTaskReminderPlanUpdateRequest request) {
        try {
            List<WorkTaskReminderStep> steps = request.getSteps().stream().map(this::step).toList();
            return WorkTaskReminderPlan.of(steps);
        } catch (RuntimeException exception) {
            throw taskSupport.invalid("Work task reminder plan is invalid");
        }
    }

    private WorkTaskReminderStep step(WorkTaskReminderStepRequest value) {
        if (value == null || value.getOffsetMinutes() == null || value.getRecipientRoles() == null) {
            throw new IllegalArgumentException();
        }
        WorkTaskReminderTiming timing = WorkTaskReminderTiming.valueOf(
                value.getTiming() == null ? "" : value.getTiming().trim().toUpperCase(Locale.ROOT));
        EnumSet<WorkTaskReminderRecipient> recipients = EnumSet.noneOf(WorkTaskReminderRecipient.class);
        for (String role : value.getRecipientRoles()) {
            WorkTaskReminderRecipient recipient = WorkTaskReminderRecipient.valueOf(
                    role == null ? "" : role.trim().toUpperCase(Locale.ROOT));
            if (!recipients.add(recipient)) throw new IllegalArgumentException("duplicate reminder recipient");
        }
        return new WorkTaskReminderStep(timing, value.getOffsetMinutes(), recipients);
    }

    private WorkTaskReminderPlanVO taskPlan(WorkTask task) {
        return new WorkTaskReminderPlanVO(task.getId().getValue(), null, task.getVersion(),
                task.getNotificationProjectionVersion(), steps(task.getReminderPlan()));
    }

    private WorkTaskReminderPlanVO seriesPlan(WorkTaskSeries series) {
        return new WorkTaskReminderPlanVO(null, series.getId().getValue(), series.getVersion(), null,
                steps(series.getReminderPlan()));
    }

    private List<WorkTaskReminderStepVO> steps(WorkTaskReminderPlan plan) {
        return plan.steps().stream().map(step -> new WorkTaskReminderStepVO(step.stepKey(),
                step.timing().name(), step.offsetMinutes(), step.recipients().stream()
                .map(Enum::name).sorted().toList())).toList();
    }

    private void requireRequest(WorkTaskReminderPlanUpdateRequest request) {
        if (request == null || request.getVersion() == null || request.getVersion() < 0
                || request.getSteps() == null) throw taskSupport.invalid("Work task reminder request is invalid");
    }

    private WorkTaskSeriesId seriesId(Long value) {
        try { return new WorkTaskSeriesId(value); }
        catch (RuntimeException exception) { throw taskSupport.invalid("Work task series ID is invalid"); }
    }

    private void requireTaskManager(AuthenticatedCaller caller, WorkTask task, MemberId memberId) {
        if (!task.isCreatedBy(memberId) && !caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE)) {
            throw taskSupport.denied("Only the work task creator or a task manager can manage the task");
        }
    }

    private void requireVersion(Long actual, Long requested) {
        if (actual == null || requested == null || !actual.equals(requested)) {
            throw new AggregateVersionConflictException();
        }
    }

    private void requireActive(TenantId tenantId, MemberId memberId, boolean denied) {
        boolean active = membershipRepository.findByMember(tenantId, memberId)
                .filter(OrganizationMembership::isActive).isPresent();
        if (!active) throw denied ? taskSupport.denied("Caller is not an active member of this tenant")
                : taskSupport.invalid("Work task member is not active");
    }

    private void lockTenant(TenantId tenantId) {
        if (!tenantRepository.lockForTenantChanges(tenantId)) {
            throw taskSupport.notFound("Work task tenant was not found");
        }
    }

    private LocalDateTime now() {
        LocalDateTime value = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return value.withNano(value.getNano() / 1_000 * 1_000);
    }
}
