package io.github.archetom.crm.api.facade;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReturnVisitCreateRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitQueryRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitUpdateRequest;
import io.github.archetom.crm.api.dto.response.ReturnVisitResponse;
import io.github.archetom.crm.api.dto.response.ReturnVisitOptionResponse;

import java.util.List;

public interface ReturnVisitFacade {
    Result<Pager<ReturnVisitResponse>> query(AuthenticatedCaller caller, ReturnVisitQueryRequest request);
    Result<List<ReturnVisitOptionResponse>> options(AuthenticatedCaller caller, String keyword, Integer limit);
    Result<ReturnVisitResponse> get(AuthenticatedCaller caller, Long returnVisitId);
    Result<ReturnVisitResponse> create(AuthenticatedCaller caller, ReturnVisitCreateRequest request);
    Result<ReturnVisitResponse> update(AuthenticatedCaller caller, Long returnVisitId,
                                       ReturnVisitUpdateRequest request);
    Result<ReturnVisitResponse> updateOwner(AuthenticatedCaller caller, Long returnVisitId,
                                            ReturnVisitOwnerUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long returnVisitId, Long version);
}
