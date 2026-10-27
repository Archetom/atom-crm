package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.NavigationPreferenceUpdateRequest;
import io.github.archetom.crm.api.dto.response.NavigationItemResponse;
import io.github.archetom.crm.api.dto.response.NavigationPreferenceResponse;
import io.github.archetom.crm.api.facade.NavigationFacade;
import io.github.archetom.crm.application.service.NavigationService;
import io.github.archetom.crm.application.vo.NavigationItemVO;
import io.github.archetom.crm.application.vo.NavigationPreferenceVO;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Facade adapter for current-caller navigation. */
@Service
@RequiredArgsConstructor
public class NavigationFacadeImpl implements NavigationFacade {

    private final NavigationService service;

    @Override
    public Result<List<NavigationItemResponse>> getNavigation(AuthenticatedCaller caller) {
        return ResultUtil.map(service.getNavigation(caller), this::toResponses);
    }

    @Override
    public Result<List<NavigationItemResponse>> getCatalog(AuthenticatedCaller caller) {
        return ResultUtil.map(service.getCatalog(caller), this::toResponses);
    }

    @Override
    public Result<NavigationPreferenceResponse> getPreference(AuthenticatedCaller caller) {
        return ResultUtil.map(service.getPreference(caller), this::toResponse);
    }

    @Override
    public Result<NavigationPreferenceResponse> updatePreference(
            AuthenticatedCaller caller, NavigationPreferenceUpdateRequest request) {
        return ResultUtil.map(service.updatePreference(caller, request), this::toResponse);
    }

    private List<NavigationItemResponse> toResponses(List<NavigationItemVO> source) {
        return source.stream().map(this::toResponse).toList();
    }

    private NavigationItemResponse toResponse(NavigationItemVO source) {
        return new NavigationItemResponse().setCode(source.code()).setParentCode(source.parentCode())
                .setRoute(source.route()).setTitleKey(source.titleKey()).setOrder(source.order())
                .setRequiredAuthority(source.requiredAuthority()).setChildren(toResponses(source.children()));
    }

    private NavigationPreferenceResponse toResponse(NavigationPreferenceVO source) {
        return new NavigationPreferenceResponse().setOrderedCodes(source.orderedCodes())
                .setHiddenCodes(source.hiddenCodes()).setVersion(source.version())
                .setAvailableItems(toResponses(source.availableItems()));
    }
}
