package io.github.archetom.crm.api.facade;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesCreateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesVersionRequest;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesOccurrenceResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesDetailResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesListItemResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesMutationResultResponse;

public interface WorkTaskSeriesFacade {
    Result<Pager<WorkTaskSeriesListItemResponse>> query(AuthenticatedCaller caller, Integer page, Integer size);
    Result<WorkTaskSeriesDetailResponse> get(AuthenticatedCaller caller, Long seriesId);
    Result<WorkTaskSeriesMutationResultResponse> create(AuthenticatedCaller caller, WorkTaskSeriesCreateRequest request);
    Result<WorkTaskSeriesMutationResultResponse> update(AuthenticatedCaller caller, Long seriesId, WorkTaskSeriesUpdateRequest request);
    Result<WorkTaskSeriesMutationResultResponse> activate(AuthenticatedCaller caller, Long seriesId, WorkTaskSeriesVersionRequest request);
    Result<WorkTaskSeriesMutationResultResponse> pause(AuthenticatedCaller caller, Long seriesId, WorkTaskSeriesVersionRequest request);
    Result<WorkTaskSeriesMutationResultResponse> delete(AuthenticatedCaller caller, Long seriesId, Long version);
    Result<Pager<WorkTaskSeriesOccurrenceResponse>> occurrences(AuthenticatedCaller caller, Long seriesId, Integer page, Integer size);
}
