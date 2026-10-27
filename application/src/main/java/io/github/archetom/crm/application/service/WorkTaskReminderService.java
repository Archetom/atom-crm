package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorkTaskReminderPlanUpdateRequest;
import io.github.archetom.crm.application.vo.WorkTaskReminderPlanVO;

public interface WorkTaskReminderService {
    Result<WorkTaskReminderPlanVO> getTaskPlan(AuthenticatedCaller caller, Long taskId);
    Result<WorkTaskReminderPlanVO> updateTaskPlan(
            AuthenticatedCaller caller, Long taskId, WorkTaskReminderPlanUpdateRequest request);
    Result<WorkTaskReminderPlanVO> getSeriesPlan(AuthenticatedCaller caller, Long seriesId);
    Result<WorkTaskReminderPlanVO> updateSeriesPlan(
            AuthenticatedCaller caller, Long seriesId, WorkTaskReminderPlanUpdateRequest request);
}
