package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReceivableQueryRequest;
import io.github.archetom.crm.application.vo.ReceivableListSummaryVO;
import io.github.archetom.crm.application.vo.ReceivableVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

public interface ReceivableQueryService {
    Result<Pager<ReceivableVO>> query(AuthenticatedCaller caller, ReceivableQueryRequest request);
    Result<ReceivableListSummaryVO> summary(
            AuthenticatedCaller caller, ReceivableQueryRequest request);
    Result<ReceivableVO> get(AuthenticatedCaller caller, Long receivableId);
}
