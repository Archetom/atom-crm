package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReturnVisitCreateRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitUpdateRequest;
import io.github.archetom.crm.application.vo.ReturnVisitVO;

public interface ReturnVisitCommandService {
    Result<ReturnVisitVO> create(AuthenticatedCaller caller, ReturnVisitCreateRequest request);
    Result<ReturnVisitVO> update(AuthenticatedCaller caller, Long returnVisitId,
                                 ReturnVisitUpdateRequest request);
    Result<ReturnVisitVO> updateOwner(AuthenticatedCaller caller, Long returnVisitId,
                                      ReturnVisitOwnerUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long returnVisitId, Long version);
}
