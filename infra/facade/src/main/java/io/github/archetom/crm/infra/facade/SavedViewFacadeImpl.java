package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.SavedViewCreateRequest;
import io.github.archetom.crm.api.dto.request.SavedViewUpdateRequest;
import io.github.archetom.crm.api.dto.response.SavedViewConditionResponse;
import io.github.archetom.crm.api.dto.response.SavedViewResponse;
import io.github.archetom.crm.api.facade.SavedViewFacade;
import io.github.archetom.crm.application.service.SavedViewService;
import io.github.archetom.crm.application.vo.SavedViewVO;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SavedViewFacadeImpl implements SavedViewFacade {

    private final SavedViewService service;

    @Override
    public Result<List<SavedViewResponse>> list(
            AuthenticatedCaller caller, String objectType, boolean includeHidden) {
        return ResultUtil.map(service.list(caller, objectType, includeHidden), values ->
                values.stream().map(this::toResponse).toList());
    }

    @Override
    public Result<SavedViewResponse> create(
            AuthenticatedCaller caller, SavedViewCreateRequest request) {
        return ResultUtil.map(service.create(caller, request), this::toResponse);
    }

    @Override
    public Result<SavedViewResponse> update(
            AuthenticatedCaller caller, Long viewId, SavedViewUpdateRequest request) {
        return ResultUtil.map(service.update(caller, viewId, request), this::toResponse);
    }

    @Override
    public Result<SavedViewResponse> setDefault(
            AuthenticatedCaller caller, Long viewId, Long version) {
        return ResultUtil.map(service.setDefault(caller, viewId, version), this::toResponse);
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long viewId, Long version) {
        return service.delete(caller, viewId, version);
    }

    private SavedViewResponse toResponse(SavedViewVO value) {
        return new SavedViewResponse().setId(value.id()).setObjectType(value.objectType())
                .setName(value.name()).setConditions(value.conditions().stream()
                        .map(condition -> new SavedViewConditionResponse()
                                .setFieldCode(condition.fieldCode()).setOperator(condition.operator())
                                .setValues(condition.values()).setExposed(condition.exposed())).toList())
                .setSortOrder(value.sortOrder()).setHidden(value.hidden())
                .setDefaultView(value.defaultView()).setVersion(value.version())
                .setCreatedTime(value.createdTime()).setUpdatedTime(value.updatedTime());
    }
}
