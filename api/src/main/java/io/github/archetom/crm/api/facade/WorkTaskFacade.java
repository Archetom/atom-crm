package io.github.archetom.crm.api.facade;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorkTaskAssigneeUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskBusinessRecordUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskCommentCreateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskCreateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskFollowerUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskQueryRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskVersionRequest;
import io.github.archetom.crm.api.dto.response.WorkTaskResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskCommentResponse;

public interface WorkTaskFacade {

    Result<Pager<WorkTaskResponse>> query(
            AuthenticatedCaller caller, WorkTaskQueryRequest request);

    Result<WorkTaskResponse> get(AuthenticatedCaller caller, Long taskId);

    Result<WorkTaskResponse> create(
            AuthenticatedCaller caller, WorkTaskCreateRequest request);

    Result<WorkTaskResponse> update(
            AuthenticatedCaller caller, Long taskId, WorkTaskUpdateRequest request);

    Result<WorkTaskResponse> updateAssignee(
            AuthenticatedCaller caller, Long taskId,
            WorkTaskAssigneeUpdateRequest request);

    Result<WorkTaskResponse> replaceFollowers(
            AuthenticatedCaller caller, Long taskId,
            WorkTaskFollowerUpdateRequest request);

    Result<WorkTaskResponse> updateBusinessRecord(
            AuthenticatedCaller caller, Long taskId,
            WorkTaskBusinessRecordUpdateRequest request);

    Result<Pager<WorkTaskCommentResponse>> queryComments(
            AuthenticatedCaller caller, Long taskId, Integer page, Integer size);

    Result<WorkTaskCommentResponse> createComment(
            AuthenticatedCaller caller, Long taskId,
            WorkTaskCommentCreateRequest request);

    Result<Void> deleteComment(
            AuthenticatedCaller caller, Long taskId, Long commentId, Long version);

    Result<WorkTaskResponse> start(
            AuthenticatedCaller caller, Long taskId, WorkTaskVersionRequest request);

    Result<WorkTaskResponse> returnToTodo(
            AuthenticatedCaller caller, Long taskId, WorkTaskVersionRequest request);

    Result<WorkTaskResponse> complete(
            AuthenticatedCaller caller, Long taskId, WorkTaskVersionRequest request);

    Result<WorkTaskResponse> cancel(
            AuthenticatedCaller caller, Long taskId, WorkTaskVersionRequest request);

    Result<Void> delete(AuthenticatedCaller caller, Long taskId, Long version);
}
