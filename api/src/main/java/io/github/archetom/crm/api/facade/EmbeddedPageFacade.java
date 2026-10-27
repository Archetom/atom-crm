package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.EmbeddedPageCreateRequest;
import io.github.archetom.crm.api.dto.request.EmbeddedPageQueryRequest;
import io.github.archetom.crm.api.dto.request.EmbeddedPageUpdateRequest;
import io.github.archetom.crm.api.dto.response.EmbeddedPageConfigurationOptionsResponse;
import io.github.archetom.crm.api.dto.response.EmbeddedPageResponse;
import io.github.archetom.crm.api.dto.response.RenderedEmbeddedPageResponse;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface EmbeddedPageFacade {
    Result<EmbeddedPageConfigurationOptionsResponse> configurationOptions(AuthenticatedCaller caller);
    Result<Pager<EmbeddedPageResponse>> query(
            AuthenticatedCaller caller, EmbeddedPageQueryRequest request);
    Result<EmbeddedPageResponse> create(
            AuthenticatedCaller caller, EmbeddedPageCreateRequest request);
    Result<EmbeddedPageResponse> update(
            AuthenticatedCaller caller, Long pageId, EmbeddedPageUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long pageId, Long version);
    Result<List<RenderedEmbeddedPageResponse>> listActive(
            AuthenticatedCaller caller, String objectType, Long recordId, String platform);
}
