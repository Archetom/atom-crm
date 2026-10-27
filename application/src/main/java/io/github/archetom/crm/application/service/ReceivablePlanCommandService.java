package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReceivablePlanBatchCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanUpdateRequest;
import io.github.archetom.crm.application.vo.ReceivablePlanVO;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface ReceivablePlanCommandService {
    Result<ReceivablePlanVO> create(AuthenticatedCaller caller, ReceivablePlanCreateRequest request);
    Result<List<ReceivablePlanVO>> createBatch(
            AuthenticatedCaller caller, ReceivablePlanBatchCreateRequest request);
    Result<ReceivablePlanVO> update(
            AuthenticatedCaller caller, Long planId, ReceivablePlanUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long planId, Long version);
}
