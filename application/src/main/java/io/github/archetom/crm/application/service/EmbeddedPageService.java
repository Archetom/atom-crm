package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.EmbeddedPageCreateRequest;
import io.github.archetom.crm.api.dto.request.EmbeddedPageQueryRequest;
import io.github.archetom.crm.api.dto.request.EmbeddedPageUpdateRequest;
import io.github.archetom.crm.application.vo.EmbeddedPageConfigurationOptionsVO;
import io.github.archetom.crm.application.vo.EmbeddedPageVO;
import io.github.archetom.crm.application.vo.RenderedEmbeddedPageVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface EmbeddedPageService {
    Result<EmbeddedPageConfigurationOptionsVO> configurationOptions(AuthenticatedCaller caller);
    Result<Pager<EmbeddedPageVO>> query(
            AuthenticatedCaller caller, EmbeddedPageQueryRequest request);
    Result<EmbeddedPageVO> create(
            AuthenticatedCaller caller, EmbeddedPageCreateRequest request);
    Result<EmbeddedPageVO> update(
            AuthenticatedCaller caller, Long pageId, EmbeddedPageUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long pageId, Long version);
    Result<List<RenderedEmbeddedPageVO>> listActive(
            AuthenticatedCaller caller, String objectType, Long recordId, String platform);
}
