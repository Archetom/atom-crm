package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OaRequestPreviewRequest;
import io.github.archetom.crm.api.dto.request.OaRequestQueryRequest;
import io.github.archetom.crm.api.dto.request.OaRequestSubmitRequest;
import io.github.archetom.crm.application.vo.OaRequestPreviewVO;
import io.github.archetom.crm.application.vo.OaRequestVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

public interface OaRequestService {
    Result<OaRequestPreviewVO> preview(
            AuthenticatedCaller caller, OaRequestPreviewRequest request);

    Result<OaRequestVO> submit(
            AuthenticatedCaller caller, OaRequestSubmitRequest request);

    Result<Pager<OaRequestVO>> query(
            AuthenticatedCaller caller, OaRequestQueryRequest request);

    Result<OaRequestVO> get(AuthenticatedCaller caller, Long requestId);
}
