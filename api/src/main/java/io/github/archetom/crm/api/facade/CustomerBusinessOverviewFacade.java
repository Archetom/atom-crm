package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.response.CustomerBusinessOverviewResponse;
import io.github.archetom.common.result.Result;

/** Public read boundary for the fixed customer-context business overview. */
public interface CustomerBusinessOverviewFacade {

    Result<CustomerBusinessOverviewResponse> get(AuthenticatedCaller caller, Long customerId);
}
