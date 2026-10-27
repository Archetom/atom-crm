package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.vo.CustomerBusinessOverviewVO;
import io.github.archetom.common.result.Result;

/** Fixed, customer-first summary of independently authorized business sources. */
public interface CustomerBusinessOverviewService {

    Result<CustomerBusinessOverviewVO> get(AuthenticatedCaller caller, Long customerId);
}
