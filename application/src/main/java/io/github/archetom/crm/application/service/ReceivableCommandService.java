package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReceivableBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.ReceivableBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableUpdateRequest;
import io.github.archetom.crm.application.vo.ReceivableVO;
import io.github.archetom.common.result.Result;

public interface ReceivableCommandService {
    Result<ReceivableVO> create(AuthenticatedCaller caller, ReceivableCreateRequest request);
    Result<ReceivableVO> update(
            AuthenticatedCaller caller, Long receivableId, ReceivableUpdateRequest request);
    Result<ReceivableVO> updateOwner(
            AuthenticatedCaller caller, Long receivableId, ReceivableOwnerUpdateRequest request);
    Result<Void> updateOwners(
            AuthenticatedCaller caller, ReceivableBatchOwnerUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long receivableId, Long version);
    Result<Void> deleteBatch(AuthenticatedCaller caller, ReceivableBatchDeleteRequest request);
}
