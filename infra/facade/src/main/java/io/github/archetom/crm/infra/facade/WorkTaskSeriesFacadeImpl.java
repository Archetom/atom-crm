package io.github.archetom.crm.infra.facade;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesCreateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesVersionRequest;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesOccurrenceResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesDetailResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesListItemResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesMutationResultResponse;
import io.github.archetom.crm.api.facade.WorkTaskSeriesFacade;
import io.github.archetom.crm.application.service.WorkTaskSeriesCapabilityService;
import io.github.archetom.crm.application.service.WorkTaskSeriesCommandService;
import io.github.archetom.crm.application.service.WorkTaskSeriesQueryService;
import io.github.archetom.crm.application.vo.WorkTaskSeriesOccurrenceVO;
import io.github.archetom.crm.application.vo.WorkTaskSeriesVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkTaskSeriesFacadeImpl implements WorkTaskSeriesFacade {
    private final WorkTaskSeriesCommandService commandService;
    private final WorkTaskSeriesQueryService queryService;
    private final WorkTaskSeriesCapabilityService capabilityService;
    @Override public Result<Pager<WorkTaskSeriesListItemResponse>> query(AuthenticatedCaller caller, Integer page, Integer size) {
        return ResultUtil.map(queryService.query(caller, page, size), value -> page(caller, value)); }
    @Override public Result<WorkTaskSeriesDetailResponse> get(AuthenticatedCaller caller, Long seriesId) {
        return ResultUtil.map(queryService.get(caller, seriesId), value -> detail(caller, value)); }
    @Override public Result<WorkTaskSeriesMutationResultResponse> create(AuthenticatedCaller caller, WorkTaskSeriesCreateRequest request) {
        return ResultUtil.map(commandService.create(caller, request), value -> mutation(caller, value)); }
    @Override public Result<WorkTaskSeriesMutationResultResponse> update(AuthenticatedCaller caller, Long seriesId, WorkTaskSeriesUpdateRequest request) {
        return ResultUtil.map(commandService.update(caller, seriesId, request), value -> mutation(caller, value)); }
    @Override public Result<WorkTaskSeriesMutationResultResponse> activate(AuthenticatedCaller caller, Long seriesId, WorkTaskSeriesVersionRequest request) {
        return ResultUtil.map(commandService.activate(caller, seriesId, request), value -> mutation(caller, value)); }
    @Override public Result<WorkTaskSeriesMutationResultResponse> pause(AuthenticatedCaller caller, Long seriesId, WorkTaskSeriesVersionRequest request) {
        return ResultUtil.map(commandService.pause(caller, seriesId, request), value -> mutation(caller, value)); }
    @Override public Result<WorkTaskSeriesMutationResultResponse> delete(AuthenticatedCaller caller, Long seriesId, Long version) {
        return ResultUtil.map(commandService.delete(caller, seriesId, version), value -> mutation(caller, value)); }
    @Override public Result<Pager<WorkTaskSeriesOccurrenceResponse>> occurrences(AuthenticatedCaller caller, Long seriesId, Integer page, Integer size) {
        return ResultUtil.map(queryService.occurrences(caller, seriesId, page, size), this::occurrencePage); }
    private Pager<WorkTaskSeriesListItemResponse> page(AuthenticatedCaller caller, Pager<WorkTaskSeriesVO> source) {
        Pager<WorkTaskSeriesListItemResponse> target = PageUtil.copy(source); target.setMeta(null);
        target.setObjectList(source.getObjectList().stream().map(value -> WorkTaskSeriesResponseAssembler.toListItem(
                value, capabilityService.resolve(caller, value))).toList()); return target;
    }
    private Pager<WorkTaskSeriesOccurrenceResponse> occurrencePage(Pager<WorkTaskSeriesOccurrenceVO> source) {
        Pager<WorkTaskSeriesOccurrenceResponse> target = PageUtil.copy(source); target.setMeta(null);
        target.setObjectList(source.getObjectList().stream().map(WorkTaskSeriesResponseAssembler::toResponse).toList()); return target;
    }
    private WorkTaskSeriesDetailResponse detail(AuthenticatedCaller caller, WorkTaskSeriesVO value) {
        return WorkTaskSeriesResponseAssembler.toDetail(value, capabilityService.resolve(caller, value));
    }
    private WorkTaskSeriesMutationResultResponse mutation(AuthenticatedCaller caller, WorkTaskSeriesVO value) {
        return WorkTaskSeriesResponseAssembler.toMutation(value, capabilityService.resolve(caller, value));
    }
}
