package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContractProductsUpdateRequest;
import io.github.archetom.crm.application.vo.ContractProductsVO;
import io.github.archetom.common.result.Result;

public interface ContractProductService {
    Result<ContractProductsVO> get(AuthenticatedCaller caller, Long contractId);

    Result<ContractProductsVO> replace(
            AuthenticatedCaller caller, Long contractId,
            ContractProductsUpdateRequest request);
}
