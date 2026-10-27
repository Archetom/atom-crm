package io.github.archetom.crm.api.facade;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.MarketingActivityBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityCreateRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityQueryRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityUpdateRequest;
import io.github.archetom.crm.api.dto.response.MarketingActivityConfigurationOptionsResponse;
import io.github.archetom.crm.api.dto.response.MarketingActivityResponse;

public interface MarketingActivityFacade {
    Result<MarketingActivityConfigurationOptionsResponse> configurationOptions(AuthenticatedCaller caller);

    Result<Pager<MarketingActivityResponse>> query(
            AuthenticatedCaller caller, MarketingActivityQueryRequest request);

    Result<MarketingActivityResponse> get(AuthenticatedCaller caller, Long activityId);

    Result<MarketingActivityResponse> create(
            AuthenticatedCaller caller, MarketingActivityCreateRequest request);

    Result<MarketingActivityResponse> update(
            AuthenticatedCaller caller, Long activityId, MarketingActivityUpdateRequest request);

    Result<Void> deleteBatch(AuthenticatedCaller caller, MarketingActivityBatchDeleteRequest request);

    Result<Void> delete(AuthenticatedCaller caller, Long activityId, Long version);
}
