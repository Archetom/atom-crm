package io.github.archetom.crm.infra.facade;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ActivityCreateRequest;
import io.github.archetom.crm.api.dto.request.ActivityQueryRequest;
import io.github.archetom.crm.api.dto.request.ActivityUpdateRequest;
import io.github.archetom.crm.api.dto.request.ActivityOptionRequest;
import io.github.archetom.crm.api.dto.response.ActivityResponse;
import io.github.archetom.crm.api.dto.response.ActivityOptionResponse;
import io.github.archetom.crm.api.facade.ActivityFacade;
import io.github.archetom.crm.application.service.ActivityCommandService;
import io.github.archetom.crm.application.service.ActivityQueryService;
import io.github.archetom.crm.application.service.ActivityOptionService;
import io.github.archetom.crm.application.vo.ActivityVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityFacadeImpl implements ActivityFacade {
    private final ActivityCommandService commandService;
    private final ActivityQueryService queryService;
    private final ActivityOptionService optionService;

    @Override
    public Result<List<ActivityOptionResponse>> options(AuthenticatedCaller caller) {
        return ResultUtil.map(optionService.query(caller), values -> values.stream()
                .map(value -> new ActivityOptionResponse().setType(value.type()).setValue(value.value())
                        .setLabel(value.label()).setDisplayOrder(value.displayOrder())).toList());
    }

    @Override
    public Result<List<ActivityOptionResponse>> replaceOptions(
            AuthenticatedCaller caller, List<ActivityOptionRequest> requests) {
        return ResultUtil.map(optionService.replace(caller, requests), values -> values.stream()
                .map(value -> new ActivityOptionResponse().setType(value.type()).setValue(value.value())
                        .setLabel(value.label()).setDisplayOrder(value.displayOrder())).toList());
    }

    @Override
    public Result<Pager<ActivityResponse>> query(AuthenticatedCaller caller, ActivityQueryRequest request) {
        return ResultUtil.map(queryService.query(caller, request), this::page);
    }

    @Override
    public Result<ActivityResponse> get(AuthenticatedCaller caller, Long activityId) {
        return ResultUtil.map(queryService.get(caller, activityId), ActivityResponseAssembler::toResponse);
    }

    @Override
    public Result<ActivityResponse> create(AuthenticatedCaller caller, ActivityCreateRequest request) {
        return ResultUtil.map(commandService.create(caller, request), ActivityResponseAssembler::toResponse);
    }

    @Override
    public Result<List<ActivityResponse>> createBatch(
            AuthenticatedCaller caller, List<ActivityCreateRequest> requests) {
        return ResultUtil.map(commandService.createBatch(caller, requests), values -> values.stream()
                .map(ActivityResponseAssembler::toResponse).toList());
    }

    @Override
    public Result<ActivityResponse> update(
            AuthenticatedCaller caller, Long activityId, ActivityUpdateRequest request) {
        return ResultUtil.map(commandService.update(caller, activityId, request),
                ActivityResponseAssembler::toResponse);
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long activityId, Long version) {
        return commandService.delete(caller, activityId, version);
    }

    private Pager<ActivityResponse> page(Pager<ActivityVO> source) {
        Pager<ActivityResponse> result = PageUtil.copy(source);
        result.setMeta(null);
        result.setObjectList(source.getObjectList().stream()
                .map(ActivityResponseAssembler::toResponse).toList());
        return result;
    }
}
