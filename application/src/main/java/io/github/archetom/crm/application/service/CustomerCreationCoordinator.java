package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CustomerCreateRequest;
import io.github.archetom.crm.application.vo.CustomerVO;

/** Reuses the customer-creation operation inside a coordinating command transaction. */
public interface CustomerCreationCoordinator {

    CustomerVO createInCurrentTransaction(
            AuthenticatedCaller caller, CustomerCreateRequest request);
}
