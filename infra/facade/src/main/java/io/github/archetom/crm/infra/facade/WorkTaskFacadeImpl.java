package io.github.archetom.crm.infra.facade;

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
import io.github.archetom.crm.api.facade.WorkTaskFacade;
import io.github.archetom.crm.application.service.WorkTaskCommandService;
import io.github.archetom.crm.application.service.WorkTaskCommentService;
import io.github.archetom.crm.application.service.WorkTaskQueryService;
import io.github.archetom.crm.application.vo.WorkTaskVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkTaskFacadeImpl implements WorkTaskFacade {

    private final WorkTaskCommandService commandService;
    private final WorkTaskQueryService queryService;
    private final WorkTaskCommentService commentService;

    @Override
    public Result<Pager<WorkTaskResponse>> query(
            AuthenticatedCaller caller, WorkTaskQueryRequest request) {
        return ResultUtil.map(queryService.query(caller, request), this::page);
    }

    @Override
    public Result<WorkTaskResponse> get(AuthenticatedCaller caller, Long taskId) {
        return ResultUtil.map(queryService.get(caller, taskId),
                WorkTaskResponseAssembler::toResponse);
    }

    @Override
    public Result<WorkTaskResponse> create(
            AuthenticatedCaller caller, WorkTaskCreateRequest request) {
        return ResultUtil.map(commandService.create(caller, request),
                WorkTaskResponseAssembler::toResponse);
    }

    @Override
    public Result<WorkTaskResponse> update(
            AuthenticatedCaller caller, Long taskId, WorkTaskUpdateRequest request) {
        return ResultUtil.map(commandService.update(caller, taskId, request),
                WorkTaskResponseAssembler::toResponse);
    }

    @Override
    public Result<WorkTaskResponse> updateAssignee(
            AuthenticatedCaller caller, Long taskId,
            WorkTaskAssigneeUpdateRequest request) {
        return ResultUtil.map(commandService.updateAssignee(caller, taskId, request),
                WorkTaskResponseAssembler::toResponse);
    }

    @Override
    public Result<WorkTaskResponse> replaceFollowers(
            AuthenticatedCaller caller, Long taskId,
            WorkTaskFollowerUpdateRequest request) {
        return ResultUtil.map(commandService.replaceFollowers(caller, taskId, request),
                WorkTaskResponseAssembler::toResponse);
    }

    @Override
    public Result<WorkTaskResponse> updateBusinessRecord(
            AuthenticatedCaller caller, Long taskId,
            WorkTaskBusinessRecordUpdateRequest request) {
        return ResultUtil.map(commandService.updateBusinessRecord(caller, taskId, request),
                WorkTaskResponseAssembler::toResponse);
    }

    @Override
    public Result<Pager<WorkTaskCommentResponse>> queryComments(
            AuthenticatedCaller caller, Long taskId, Integer page, Integer size) {
        return ResultUtil.map(commentService.query(caller, taskId, page, size), source -> {
            Pager<WorkTaskCommentResponse> target = PageUtil.copy(source);
            target.setMeta(null);
            target.setObjectList(source.getObjectList().stream()
                    .map(WorkTaskResponseAssembler::toResponse).toList());
            return target;
        });
    }

    @Override
    public Result<WorkTaskCommentResponse> createComment(
            AuthenticatedCaller caller, Long taskId,
            WorkTaskCommentCreateRequest request) {
        return ResultUtil.map(commentService.create(caller, taskId, request),
                WorkTaskResponseAssembler::toResponse);
    }

    @Override
    public Result<Void> deleteComment(
            AuthenticatedCaller caller, Long taskId, Long commentId, Long version) {
        return commentService.delete(caller, taskId, commentId, version);
    }

    @Override
    public Result<WorkTaskResponse> start(
            AuthenticatedCaller caller, Long taskId, WorkTaskVersionRequest request) {
        return ResultUtil.map(commandService.start(caller, taskId, request),
                WorkTaskResponseAssembler::toResponse);
    }

    @Override
    public Result<WorkTaskResponse> returnToTodo(
            AuthenticatedCaller caller, Long taskId, WorkTaskVersionRequest request) {
        return ResultUtil.map(commandService.returnToTodo(caller, taskId, request),
                WorkTaskResponseAssembler::toResponse);
    }

    @Override
    public Result<WorkTaskResponse> complete(
            AuthenticatedCaller caller, Long taskId, WorkTaskVersionRequest request) {
        return ResultUtil.map(commandService.complete(caller, taskId, request),
                WorkTaskResponseAssembler::toResponse);
    }

    @Override
    public Result<WorkTaskResponse> cancel(
            AuthenticatedCaller caller, Long taskId, WorkTaskVersionRequest request) {
        return ResultUtil.map(commandService.cancel(caller, taskId, request),
                WorkTaskResponseAssembler::toResponse);
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long taskId, Long version) {
        return commandService.delete(caller, taskId, version);
    }

    private Pager<WorkTaskResponse> page(Pager<WorkTaskVO> source) {
        Pager<WorkTaskResponse> target = PageUtil.copy(source);
        target.setMeta(null);
        target.setObjectList(source.getObjectList().stream()
                .map(WorkTaskResponseAssembler::toResponse).toList());
        return target;
    }
}
