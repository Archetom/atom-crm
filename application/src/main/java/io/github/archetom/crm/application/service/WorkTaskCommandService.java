package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorkTaskAssigneeUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskBusinessRecordUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskCreateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskFollowerUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskVersionRequest;
import io.github.archetom.crm.application.vo.WorkTaskVO;

public interface WorkTaskCommandService {

    Result<WorkTaskVO> create(
            AuthenticatedCaller caller, WorkTaskCreateRequest request);

    Result<WorkTaskVO> update(
            AuthenticatedCaller caller, Long taskId, WorkTaskUpdateRequest request);

    Result<WorkTaskVO> updateAssignee(
            AuthenticatedCaller caller, Long taskId,
            WorkTaskAssigneeUpdateRequest request);

    Result<WorkTaskVO> replaceFollowers(
            AuthenticatedCaller caller, Long taskId,
            WorkTaskFollowerUpdateRequest request);

    Result<WorkTaskVO> updateBusinessRecord(
            AuthenticatedCaller caller, Long taskId,
            WorkTaskBusinessRecordUpdateRequest request);

    Result<WorkTaskVO> start(
            AuthenticatedCaller caller, Long taskId, WorkTaskVersionRequest request);

    Result<WorkTaskVO> returnToTodo(
            AuthenticatedCaller caller, Long taskId, WorkTaskVersionRequest request);

    Result<WorkTaskVO> complete(
            AuthenticatedCaller caller, Long taskId, WorkTaskVersionRequest request);

    Result<WorkTaskVO> cancel(
            AuthenticatedCaller caller, Long taskId, WorkTaskVersionRequest request);

    Result<Void> delete(AuthenticatedCaller caller, Long taskId, Long version);
}
