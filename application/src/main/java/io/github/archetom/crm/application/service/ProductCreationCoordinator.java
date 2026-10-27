package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ProductCreateRequest;
import io.github.archetom.crm.application.vo.ProductVO;

/** Reuses product creation while a composite command already owns the transaction. */
public interface ProductCreationCoordinator {

    ProductVO createInCurrentTransaction(AuthenticatedCaller caller, ProductCreateRequest request);
}
