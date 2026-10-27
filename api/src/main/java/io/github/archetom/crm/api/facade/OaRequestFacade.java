package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OaRequestPreviewRequest;
import io.github.archetom.crm.api.dto.request.OaRequestQueryRequest;
import io.github.archetom.crm.api.dto.request.OaRequestSubmitRequest;
import io.github.archetom.crm.api.dto.response.OaRequestPreviewResponse;
import io.github.archetom.crm.api.dto.response.OaRequestResponse;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

public interface OaRequestFacade {
    Result<OaRequestPreviewResponse> preview(
            AuthenticatedCaller caller, OaRequestPreviewRequest request);

    Result<OaRequestResponse> submit(
            AuthenticatedCaller caller, OaRequestSubmitRequest request);

    Result<Pager<OaRequestResponse>> query(
            AuthenticatedCaller caller, OaRequestQueryRequest request);

    Result<OaRequestResponse> get(AuthenticatedCaller caller, Long requestId);
}
