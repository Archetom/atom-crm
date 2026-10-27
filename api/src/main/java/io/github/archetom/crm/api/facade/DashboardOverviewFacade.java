package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.response.DashboardOverviewResponse;
import io.github.archetom.common.result.Result;

/** Public facade for the current member's fixed sales dashboard. */
public interface DashboardOverviewFacade {

    Result<DashboardOverviewResponse> get(AuthenticatedCaller caller);
}
