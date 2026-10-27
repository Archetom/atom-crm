package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OpportunityProductsUpdateRequest;
import io.github.archetom.crm.application.vo.OpportunityProductsVO;
import io.github.archetom.common.result.Result;

public interface OpportunityProductService {
    Result<OpportunityProductsVO> get(AuthenticatedCaller caller, Long opportunityId);
    Result<OpportunityProductsVO> replace(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityProductsUpdateRequest request);
}
