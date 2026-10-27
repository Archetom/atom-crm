package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.NavigationPreferenceUpdateRequest;
import io.github.archetom.crm.application.vo.NavigationItemVO;
import io.github.archetom.crm.application.vo.NavigationPreferenceVO;
import io.github.archetom.common.result.Result;

import java.util.List;

/** Public application boundary for current-caller navigation. */
public interface NavigationService {

    Result<List<NavigationItemVO>> getNavigation(AuthenticatedCaller caller);

    Result<List<NavigationItemVO>> getCatalog(AuthenticatedCaller caller);

    Result<NavigationPreferenceVO> getPreference(AuthenticatedCaller caller);

    Result<NavigationPreferenceVO> updatePreference(
            AuthenticatedCaller caller, NavigationPreferenceUpdateRequest request);
}
