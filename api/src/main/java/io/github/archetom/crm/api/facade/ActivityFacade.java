package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ActivityCreateRequest;
import io.github.archetom.crm.api.dto.request.ActivityQueryRequest;
import io.github.archetom.crm.api.dto.request.ActivityUpdateRequest;
import io.github.archetom.crm.api.dto.request.ActivityOptionRequest;
import io.github.archetom.crm.api.dto.response.ActivityResponse;
import io.github.archetom.crm.api.dto.response.ActivityOptionResponse;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface ActivityFacade {
    Result<Pager<ActivityResponse>> query(AuthenticatedCaller caller, ActivityQueryRequest request);
    Result<List<ActivityOptionResponse>> options(AuthenticatedCaller caller);
    Result<List<ActivityOptionResponse>> replaceOptions(
            AuthenticatedCaller caller, List<ActivityOptionRequest> requests);
    Result<ActivityResponse> get(AuthenticatedCaller caller, Long activityId);
    Result<ActivityResponse> create(AuthenticatedCaller caller, ActivityCreateRequest request);
    Result<List<ActivityResponse>> createBatch(AuthenticatedCaller caller, List<ActivityCreateRequest> requests);
    Result<ActivityResponse> update(AuthenticatedCaller caller, Long activityId, ActivityUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long activityId, Long version);
}
