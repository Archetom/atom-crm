package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReceivableBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.ReceivableBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableQueryRequest;
import io.github.archetom.crm.api.dto.request.ReceivableUpdateRequest;
import io.github.archetom.crm.api.dto.response.ReceivableListSummaryResponse;
import io.github.archetom.crm.api.dto.response.ReceivableResponse;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

public interface ReceivableFacade {
    Result<Pager<ReceivableResponse>> query(AuthenticatedCaller caller, ReceivableQueryRequest request);
    Result<ReceivableListSummaryResponse> summary(
            AuthenticatedCaller caller, ReceivableQueryRequest request);
    Result<ReceivableResponse> get(AuthenticatedCaller caller, Long receivableId);
    Result<ReceivableResponse> create(AuthenticatedCaller caller, ReceivableCreateRequest request);
    Result<ReceivableResponse> update(
            AuthenticatedCaller caller, Long receivableId, ReceivableUpdateRequest request);
    Result<ReceivableResponse> updateOwner(
            AuthenticatedCaller caller, Long receivableId, ReceivableOwnerUpdateRequest request);
    Result<Void> updateOwners(
            AuthenticatedCaller caller, ReceivableBatchOwnerUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long receivableId, Long version);
    Result<Void> deleteBatch(AuthenticatedCaller caller, ReceivableBatchDeleteRequest request);
}
