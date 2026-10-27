package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.EmbeddedPageCreateRequest;
import io.github.archetom.crm.api.dto.request.EmbeddedPageQueryRequest;
import io.github.archetom.crm.api.dto.request.EmbeddedPageUpdateRequest;
import io.github.archetom.crm.api.dto.response.BusinessObjectResponse;
import io.github.archetom.crm.api.dto.response.EmbeddedPageConfigurationOptionsResponse;
import io.github.archetom.crm.api.dto.response.EmbeddedPageResponse;
import io.github.archetom.crm.api.dto.response.RenderedEmbeddedPageResponse;
import io.github.archetom.crm.api.facade.EmbeddedPageFacade;
import io.github.archetom.crm.application.service.EmbeddedPageService;
import io.github.archetom.crm.application.vo.EmbeddedPageVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmbeddedPageFacadeImpl implements EmbeddedPageFacade {

    private final EmbeddedPageService service;

    @Override
    public Result<EmbeddedPageConfigurationOptionsResponse> configurationOptions(
            AuthenticatedCaller caller) {
        return ResultUtil.map(service.configurationOptions(caller), value ->
                new EmbeddedPageConfigurationOptionsResponse(value.objectTypes().stream()
                        .map(item -> new BusinessObjectResponse().setCode(item.code())
                                .setDisplayName(item.displayName()))
                        .toList(), value.platforms(), value.statuses()));
    }

    @Override
    public Result<Pager<EmbeddedPageResponse>> query(
            AuthenticatedCaller caller, EmbeddedPageQueryRequest request) {
        return ResultUtil.map(service.query(caller, request), this::toPager);
    }

    @Override
    public Result<EmbeddedPageResponse> create(
            AuthenticatedCaller caller, EmbeddedPageCreateRequest request) {
        return ResultUtil.map(service.create(caller, request), this::toResponse);
    }

    @Override
    public Result<EmbeddedPageResponse> update(
            AuthenticatedCaller caller, Long pageId, EmbeddedPageUpdateRequest request) {
        return ResultUtil.map(service.update(caller, pageId, request), this::toResponse);
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long pageId, Long version) {
        return service.delete(caller, pageId, version);
    }

    @Override
    public Result<List<RenderedEmbeddedPageResponse>> listActive(
            AuthenticatedCaller caller, String objectType, Long recordId, String platform) {
        return ResultUtil.map(service.listActive(caller, objectType, recordId, platform), values ->
                values.stream().map(value -> new RenderedEmbeddedPageResponse().setId(value.id())
                        .setName(value.name()).setDescription(value.description()).setUrl(value.url())
                        .setSortOrder(value.sortOrder())).toList());
    }

    private Pager<EmbeddedPageResponse> toPager(Pager<EmbeddedPageVO> page) {
        Pager<EmbeddedPageResponse> pager = PageUtil.copy(page);
        pager.setMeta(null);
        pager.setObjectList(page.getObjectList().stream().map(this::toResponse).toList());
        return pager;
    }

    private EmbeddedPageResponse toResponse(EmbeddedPageVO value) {
        return new EmbeddedPageResponse().setId(value.id()).setObjectType(value.objectType())
                .setPlatform(value.platform()).setName(value.name()).setDescription(value.description())
                .setUrlTemplate(value.urlTemplate()).setSortOrder(value.sortOrder())
                .setStatus(value.status()).setCreatedByActorId(value.createdByActorId())
                .setVersion(value.version()).setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }
}
