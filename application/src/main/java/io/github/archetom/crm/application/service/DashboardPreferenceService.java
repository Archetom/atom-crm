package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.DashboardPreferenceUpdateRequest;
import io.github.archetom.crm.application.vo.DashboardPreferenceVO;
import io.github.archetom.common.result.Result;

/** Application boundary for current-member dashboard defaults. */
public interface DashboardPreferenceService {

    Result<DashboardPreferenceVO> get(AuthenticatedCaller caller);

    Result<DashboardPreferenceVO> update(
            AuthenticatedCaller caller, DashboardPreferenceUpdateRequest request);
}
