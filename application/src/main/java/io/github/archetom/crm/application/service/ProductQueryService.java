package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ProductQueryRequest;
import io.github.archetom.crm.application.vo.ProductCategoryVO;
import io.github.archetom.crm.application.vo.ProductEditableBaselineVO;
import io.github.archetom.crm.application.vo.ProductVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface ProductQueryService {
    Result<Pager<ProductVO>> query(
            AuthenticatedCaller caller, ProductQueryRequest request);
    Result<ProductVO> get(AuthenticatedCaller caller, Long productId);
    Result<ProductEditableBaselineVO> editableBaseline(
            AuthenticatedCaller caller, Long productId);
    Result<List<ProductCategoryVO>> categoryTree(AuthenticatedCaller caller);
    Result<ProductCategoryVO> getCategory(AuthenticatedCaller caller, Long categoryId);
}
