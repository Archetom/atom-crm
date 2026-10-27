package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.DashboardPreferenceUpdateRequest;
import io.github.archetom.crm.api.dto.response.DashboardPreferenceResponse;
import io.github.archetom.common.result.Result;

/** Public boundary for current-member dashboard defaults. */
public interface DashboardPreferenceFacade {

    Result<DashboardPreferenceResponse> get(AuthenticatedCaller caller);

    Result<DashboardPreferenceResponse> update(
            AuthenticatedCaller caller, DashboardPreferenceUpdateRequest request);
}
