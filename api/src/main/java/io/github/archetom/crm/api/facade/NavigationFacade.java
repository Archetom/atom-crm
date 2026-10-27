package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.NavigationPreferenceUpdateRequest;
import io.github.archetom.crm.api.dto.response.NavigationItemResponse;
import io.github.archetom.crm.api.dto.response.NavigationPreferenceResponse;
import io.github.archetom.common.result.Result;

import java.util.List;

/** Public current-caller navigation facade. */
public interface NavigationFacade {

    Result<List<NavigationItemResponse>> getNavigation(AuthenticatedCaller caller);

    Result<List<NavigationItemResponse>> getCatalog(AuthenticatedCaller caller);

    Result<NavigationPreferenceResponse> getPreference(AuthenticatedCaller caller);

    Result<NavigationPreferenceResponse> updatePreference(
            AuthenticatedCaller caller, NavigationPreferenceUpdateRequest request);
}
