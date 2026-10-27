package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ActivityQueryRequest;
import io.github.archetom.crm.application.vo.ActivityVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

public interface ActivityQueryService {
    Result<Pager<ActivityVO>> query(AuthenticatedCaller caller, ActivityQueryRequest request);
    Result<ActivityVO> get(AuthenticatedCaller caller, Long activityId);
}
