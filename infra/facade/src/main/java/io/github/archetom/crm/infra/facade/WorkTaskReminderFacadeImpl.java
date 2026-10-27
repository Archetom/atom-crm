package io.github.archetom.crm.infra.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorkTaskReminderPlanUpdateRequest;
import io.github.archetom.crm.api.dto.response.WorkTaskReminderPlanResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskReminderStepResponse;
import io.github.archetom.crm.api.facade.WorkTaskReminderFacade;
import io.github.archetom.crm.application.service.WorkTaskReminderService;
import io.github.archetom.crm.application.vo.WorkTaskReminderPlanVO;
import io.github.archetom.crm.shared.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkTaskReminderFacadeImpl implements WorkTaskReminderFacade {
    private final WorkTaskReminderService service;

    @Override public Result<WorkTaskReminderPlanResponse> getTaskPlan(AuthenticatedCaller caller, Long taskId) {
        return ResultUtil.map(service.getTaskPlan(caller, taskId), this::response);
    }
    @Override public Result<WorkTaskReminderPlanResponse> updateTaskPlan(
            AuthenticatedCaller caller, Long taskId, WorkTaskReminderPlanUpdateRequest request) {
        return ResultUtil.map(service.updateTaskPlan(caller, taskId, request), this::response);
    }
    @Override public Result<WorkTaskReminderPlanResponse> getSeriesPlan(AuthenticatedCaller caller, Long seriesId) {
        return ResultUtil.map(service.getSeriesPlan(caller, seriesId), this::response);
    }
    @Override public Result<WorkTaskReminderPlanResponse> updateSeriesPlan(
            AuthenticatedCaller caller, Long seriesId, WorkTaskReminderPlanUpdateRequest request) {
        return ResultUtil.map(service.updateSeriesPlan(caller, seriesId, request), this::response);
    }

    private WorkTaskReminderPlanResponse response(WorkTaskReminderPlanVO value) {
        return new WorkTaskReminderPlanResponse().setTaskId(value.taskId()).setSeriesId(value.seriesId())
                .setVersion(value.version()).setNotificationProjectionVersion(value.notificationProjectionVersion())
                .setSteps(value.steps().stream().map(step -> new WorkTaskReminderStepResponse()
                        .setStepKey(step.stepKey()).setTiming(step.timing())
                        .setOffsetMinutes(step.offsetMinutes()).setRecipientRoles(step.recipientRoles())).toList());
    }
}
