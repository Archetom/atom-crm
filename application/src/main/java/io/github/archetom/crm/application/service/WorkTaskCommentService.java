package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorkTaskCommentCreateRequest;
import io.github.archetom.crm.application.vo.WorkTaskCommentVO;

public interface WorkTaskCommentService {

    Result<Pager<WorkTaskCommentVO>> query(
            AuthenticatedCaller caller, Long taskId, Integer page, Integer size);

    Result<WorkTaskCommentVO> create(
            AuthenticatedCaller caller, Long taskId, WorkTaskCommentCreateRequest request);

    Result<Void> delete(
            AuthenticatedCaller caller, Long taskId, Long commentId, Long version);
}
