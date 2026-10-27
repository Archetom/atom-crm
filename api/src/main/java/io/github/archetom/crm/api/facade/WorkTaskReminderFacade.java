package io.github.archetom.crm.api.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorkTaskReminderPlanUpdateRequest;
import io.github.archetom.crm.api.dto.response.WorkTaskReminderPlanResponse;

public interface WorkTaskReminderFacade {
    Result<WorkTaskReminderPlanResponse> getTaskPlan(AuthenticatedCaller caller, Long taskId);
    Result<WorkTaskReminderPlanResponse> updateTaskPlan(
            AuthenticatedCaller caller, Long taskId, WorkTaskReminderPlanUpdateRequest request);
    Result<WorkTaskReminderPlanResponse> getSeriesPlan(AuthenticatedCaller caller, Long seriesId);
    Result<WorkTaskReminderPlanResponse> updateSeriesPlan(
            AuthenticatedCaller caller, Long seriesId, WorkTaskReminderPlanUpdateRequest request);
}
