package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.MarketingActivityBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityCreateRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityQueryRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityUpdateRequest;
import io.github.archetom.crm.application.vo.MarketingActivityConfigurationOptionsVO;
import io.github.archetom.crm.application.vo.MarketingActivityVO;

public interface MarketingActivityService {
    Result<MarketingActivityConfigurationOptionsVO> configurationOptions(AuthenticatedCaller caller);

    Result<Pager<MarketingActivityVO>> query(
            AuthenticatedCaller caller, MarketingActivityQueryRequest request);

    Result<MarketingActivityVO> get(AuthenticatedCaller caller, Long activityId);

    Result<MarketingActivityVO> create(
            AuthenticatedCaller caller, MarketingActivityCreateRequest request);

    Result<MarketingActivityVO> update(
            AuthenticatedCaller caller, Long activityId, MarketingActivityUpdateRequest request);

    Result<Void> deleteBatch(AuthenticatedCaller caller, MarketingActivityBatchDeleteRequest request);

    Result<Void> delete(AuthenticatedCaller caller, Long activityId, Long version);
}
