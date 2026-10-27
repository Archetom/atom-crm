package io.github.archetom.crm.api.facade;

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
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface ProductFacade {
    Result<Pager<ProductResponse>> query(
            AuthenticatedCaller caller, ProductQueryRequest request);
    Result<ProductResponse> get(AuthenticatedCaller caller, Long productId);
    Result<ProductEditableBaselineResponse> editableBaseline(
            AuthenticatedCaller caller, Long productId);
    Result<ProductResponse> create(
            AuthenticatedCaller caller, ProductCreateRequest request);
    Result<ProductResponse> update(
            AuthenticatedCaller caller, Long productId, ProductUpdateRequest request);
    Result<ProductResponse> updateAvailability(
            AuthenticatedCaller caller, Long productId,
            ProductAvailabilityUpdateRequest request);
    Result<ProductResponse> updateOwner(
            AuthenticatedCaller caller, Long productId,
            ProductOwnerUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long productId, Long version);
    Result<List<ProductCategoryResponse>> categoryTree(AuthenticatedCaller caller);
    Result<ProductCategoryResponse> getCategory(
            AuthenticatedCaller caller, Long categoryId);
    Result<ProductCategoryResponse> createCategory(
            AuthenticatedCaller caller, ProductCategoryCreateRequest request);
    Result<ProductCategoryResponse> updateCategory(
            AuthenticatedCaller caller, Long categoryId,
            ProductCategoryUpdateRequest request);
    Result<Void> deleteCategory(
            AuthenticatedCaller caller, Long categoryId, Long version);
}
