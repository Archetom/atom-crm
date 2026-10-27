package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.vo.WorkTaskSeriesOccurrenceVO;
import io.github.archetom.crm.application.vo.WorkTaskSeriesVO;

public interface WorkTaskSeriesQueryService {
    Result<Pager<WorkTaskSeriesVO>> query(AuthenticatedCaller caller, Integer page, Integer size);
    Result<WorkTaskSeriesVO> get(AuthenticatedCaller caller, Long seriesId);
    Result<Pager<WorkTaskSeriesOccurrenceVO>> occurrences(AuthenticatedCaller caller, Long seriesId, Integer page, Integer size);
}
