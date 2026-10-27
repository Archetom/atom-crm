package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ProductAvailabilityUpdateRequest;
import io.github.archetom.crm.api.dto.request.ProductCategoryCreateRequest;
import io.github.archetom.crm.api.dto.request.ProductCategoryUpdateRequest;
import io.github.archetom.crm.api.dto.request.ProductCreateRequest;
import io.github.archetom.crm.api.dto.request.ProductOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ProductUpdateRequest;
import io.github.archetom.crm.application.vo.ProductCategoryVO;
import io.github.archetom.crm.application.vo.ProductVO;
import io.github.archetom.common.result.Result;

public interface ProductCommandService {
    Result<ProductVO> create(AuthenticatedCaller caller, ProductCreateRequest request);
    Result<ProductVO> update(AuthenticatedCaller caller, Long productId,
                             ProductUpdateRequest request);
    Result<ProductVO> updateAvailability(AuthenticatedCaller caller, Long productId,
                                         ProductAvailabilityUpdateRequest request);
    Result<ProductVO> updateOwner(AuthenticatedCaller caller, Long productId,
                                  ProductOwnerUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long productId, Long version);
    Result<ProductCategoryVO> createCategory(
            AuthenticatedCaller caller, ProductCategoryCreateRequest request);
    Result<ProductCategoryVO> updateCategory(
            AuthenticatedCaller caller, Long categoryId,
            ProductCategoryUpdateRequest request);
    Result<Void> deleteCategory(
            AuthenticatedCaller caller, Long categoryId, Long version);
}
