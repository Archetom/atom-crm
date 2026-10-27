package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReceivablePlanBatchCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanQueryRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanUpdateRequest;
import io.github.archetom.crm.api.dto.response.ReceivablePlanResponse;
import io.github.archetom.crm.api.dto.response.ReceivablePlanListSummaryResponse;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface ReceivablePlanFacade {
    Result<Pager<ReceivablePlanResponse>> query(
            AuthenticatedCaller caller, ReceivablePlanQueryRequest request);
    Result<ReceivablePlanListSummaryResponse> summary(
            AuthenticatedCaller caller, ReceivablePlanQueryRequest request);
    Result<ReceivablePlanResponse> get(AuthenticatedCaller caller, Long planId);
    Result<List<ReceivablePlanResponse>> available(AuthenticatedCaller caller, Long contractId);
    Result<ReceivablePlanResponse> create(
            AuthenticatedCaller caller, ReceivablePlanCreateRequest request);
    Result<List<ReceivablePlanResponse>> createBatch(
            AuthenticatedCaller caller, ReceivablePlanBatchCreateRequest request);
    Result<ReceivablePlanResponse> update(
            AuthenticatedCaller caller, Long planId, ReceivablePlanUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long planId, Long version);
}
