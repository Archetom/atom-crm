package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ActivityCreateRequest;
import io.github.archetom.crm.api.dto.request.ActivityUpdateRequest;
import io.github.archetom.crm.application.vo.ActivityVO;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface ActivityCommandService {
    Result<ActivityVO> create(AuthenticatedCaller caller, ActivityCreateRequest request);
    Result<List<ActivityVO>> createBatch(AuthenticatedCaller caller, List<ActivityCreateRequest> requests);
    Result<ActivityVO> update(AuthenticatedCaller caller, Long activityId, ActivityUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long activityId, Long version);
}
