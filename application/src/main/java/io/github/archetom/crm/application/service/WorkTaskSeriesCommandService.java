package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesCreateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesVersionRequest;
import io.github.archetom.crm.application.vo.WorkTaskSeriesVO;

public interface WorkTaskSeriesCommandService {
    Result<WorkTaskSeriesVO> create(AuthenticatedCaller caller, WorkTaskSeriesCreateRequest request);
    Result<WorkTaskSeriesVO> update(AuthenticatedCaller caller, Long seriesId, WorkTaskSeriesUpdateRequest request);
    Result<WorkTaskSeriesVO> activate(AuthenticatedCaller caller, Long seriesId, WorkTaskSeriesVersionRequest request);
    Result<WorkTaskSeriesVO> pause(AuthenticatedCaller caller, Long seriesId, WorkTaskSeriesVersionRequest request);
    Result<WorkTaskSeriesVO> delete(AuthenticatedCaller caller, Long seriesId, Long version);
}
