package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReturnVisitQueryRequest;
import io.github.archetom.crm.application.vo.ReturnVisitVO;
import io.github.archetom.crm.application.vo.ReturnVisitOptionVO;

import java.util.List;

public interface ReturnVisitQueryService {
    Result<Pager<ReturnVisitVO>> query(AuthenticatedCaller caller, ReturnVisitQueryRequest request);
    Result<List<ReturnVisitOptionVO>> options(AuthenticatedCaller caller, String keyword, Integer limit);
    Result<ReturnVisitVO> get(AuthenticatedCaller caller, Long returnVisitId);
}
