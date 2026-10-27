package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.vo.DashboardOverviewVO;
import io.github.archetom.common.result.Result;

/** Permission-aware fixed sales dashboard overview. */
public interface DashboardOverviewService {

    Result<DashboardOverviewVO> get(AuthenticatedCaller caller);
}
