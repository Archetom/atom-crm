package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ProductAvailabilityUpdateRequest;
import io.github.archetom.crm.api.dto.request.ProductCategoryCreateRequest;
import io.github.archetom.crm.api.dto.request.ProductCategoryUpdateRequest;
import io.github.archetom.crm.api.dto.request.ProductCreateRequest;
import io.github.archetom.crm.api.dto.request.ProductOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ProductQueryRequest;
import io.github.archetom.crm.api.dto.request.ProductUpdateRequest;
import io.github.archetom.crm.api.dto.response.ProductCategoryResponse;
import io.github.archetom.crm.api.dto.response.ProductEditableBaselineResponse;
import io.github.archetom.crm.api.dto.response.ProductResponse;
import io.github.archetom.crm.api.facade.ProductFacade;
import io.github.archetom.crm.application.service.ProductCommandService;
import io.github.archetom.crm.application.service.ProductQueryService;
import io.github.archetom.crm.application.vo.ProductVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductFacadeImpl implements ProductFacade {

    private final ProductCommandService commandService;
    private final ProductQueryService queryService;
    private final FileFieldResponseEnricher fileFieldResponseEnricher;

    @Override
    public Result<Pager<ProductResponse>> query(
            AuthenticatedCaller caller, ProductQueryRequest request) {
        return ResultUtil.map(queryService.query(caller, request), source -> page(caller, source));
    }

    @Override
    public Result<ProductResponse> get(AuthenticatedCaller caller, Long productId) {
        return ResultUtil.map(queryService.get(caller, productId), value -> response(caller, value));
    }

    @Override
    public Result<ProductEditableBaselineResponse> editableBaseline(
            AuthenticatedCaller caller, Long productId) {
        return ResultUtil.map(queryService.editableBaseline(caller, productId),
                ProductResponseAssembler::toResponse);
    }

    @Override
    public Result<ProductResponse> create(
            AuthenticatedCaller caller, ProductCreateRequest request) {
        return ResultUtil.map(commandService.create(caller, request), value -> response(caller, value));
    }

    @Override
    public Result<ProductResponse> update(
            AuthenticatedCaller caller, Long productId, ProductUpdateRequest request) {
        return ResultUtil.map(commandService.update(caller, productId, request), value -> response(caller, value));
    }

    @Override
    public Result<ProductResponse> updateAvailability(
            AuthenticatedCaller caller, Long productId,
            ProductAvailabilityUpdateRequest request) {
        return ResultUtil.map(commandService.updateAvailability(
                        caller, productId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<ProductResponse> updateOwner(
            AuthenticatedCaller caller, Long productId,
            ProductOwnerUpdateRequest request) {
        return ResultUtil.map(commandService.updateOwner(caller, productId, request), value -> response(caller, value));
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long productId, Long version) {
        return commandService.delete(caller, productId, version);
    }

    @Override
    public Result<List<ProductCategoryResponse>> categoryTree(
            AuthenticatedCaller caller) {
        return ResultUtil.map(queryService.categoryTree(caller), values -> values.stream()
                .map(ProductResponseAssembler::toResponse).toList());
    }

    @Override
    public Result<ProductCategoryResponse> getCategory(
            AuthenticatedCaller caller, Long categoryId) {
        return ResultUtil.map(queryService.getCategory(caller, categoryId),
                ProductResponseAssembler::toResponse);
    }

    @Override
    public Result<ProductCategoryResponse> createCategory(
            AuthenticatedCaller caller, ProductCategoryCreateRequest request) {
        return ResultUtil.map(commandService.createCategory(caller, request),
                ProductResponseAssembler::toResponse);
    }

    @Override
    public Result<ProductCategoryResponse> updateCategory(
            AuthenticatedCaller caller, Long categoryId,
            ProductCategoryUpdateRequest request) {
        return ResultUtil.map(commandService.updateCategory(
                        caller, categoryId, request),
                ProductResponseAssembler::toResponse);
    }

    @Override
    public Result<Void> deleteCategory(
            AuthenticatedCaller caller, Long categoryId, Long version) {
        return commandService.deleteCategory(caller, categoryId, version);
    }

    private ProductResponse response(AuthenticatedCaller caller, ProductVO value) {
        return response(value, fileFieldResponseEnricher.crm(caller, "product", value.id()));
    }

    private static ProductResponse response(
            ProductVO value, java.util.Map<String, java.util.List<io.github.archetom.crm.api.dto.response.AttachmentResponse>> fileFields) {
        return ProductResponseAssembler.toResponse(value).setFileFields(fileFields);
    }

    /** One FILE-value read for the whole projection instead of one per row. */
    private java.util.List<ProductResponse> responses(
            AuthenticatedCaller caller, java.util.List<ProductVO> values) {
        var fileFields = fileFieldResponseEnricher.crm(
                caller, "product", values.stream().map(value -> value.id()).toList());
        return values.stream()
                .map(value -> response(value, fileFields.getOrDefault(value.id(), java.util.Map.of())))
                .toList();
    }

    private Pager<ProductResponse> page(AuthenticatedCaller caller, Pager<ProductVO> source) {
        Pager<ProductResponse> target = PageUtil.copy(source);
        target.setMeta(null);
        target.setObjectList(responses(caller, source.getObjectList()));
        return target;
    }
}
