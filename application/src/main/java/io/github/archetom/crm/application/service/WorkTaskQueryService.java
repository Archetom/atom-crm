package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorkTaskQueryRequest;
import io.github.archetom.crm.application.vo.WorkTaskVO;

public interface WorkTaskQueryService {

    Result<Pager<WorkTaskVO>> query(
            AuthenticatedCaller caller, WorkTaskQueryRequest request);

    Result<WorkTaskVO> get(AuthenticatedCaller caller, Long taskId);
}
