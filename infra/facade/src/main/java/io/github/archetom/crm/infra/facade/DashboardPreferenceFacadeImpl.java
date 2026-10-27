package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.DashboardPreferenceUpdateRequest;
import io.github.archetom.crm.api.dto.response.DashboardPreferenceResponse;
import io.github.archetom.crm.api.facade.DashboardPreferenceFacade;
import io.github.archetom.crm.application.service.DashboardPreferenceService;
import io.github.archetom.crm.application.vo.DashboardPreferenceVO;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Facade adapter for current-member dashboard defaults. */
@Service
@RequiredArgsConstructor
public class DashboardPreferenceFacadeImpl implements DashboardPreferenceFacade {

    private final DashboardPreferenceService service;

    @Override
    public Result<DashboardPreferenceResponse> get(AuthenticatedCaller caller) {
        return ResultUtil.map(service.get(caller), this::toResponse);
    }

    @Override
    public Result<DashboardPreferenceResponse> update(
            AuthenticatedCaller caller, DashboardPreferenceUpdateRequest request) {
        return ResultUtil.map(service.update(caller, request), this::toResponse);
    }

    private DashboardPreferenceResponse toResponse(DashboardPreferenceVO source) {
        return new DashboardPreferenceResponse()
                .setTimeRange(source.timeRange())
                .setCustomStartDate(source.customStartDate())
                .setCustomEndDate(source.customEndDate())
                .setDataScope(source.dataScope())
                .setHiddenModules(source.hiddenModules())
                .setVersion(source.version());
    }
}
