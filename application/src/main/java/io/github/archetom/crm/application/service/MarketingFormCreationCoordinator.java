package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.MarketingFormCreateRequest;
import io.github.archetom.crm.application.vo.MarketingFormVO;

/** Creates a public lead-capture form inside a caller-owned composite command transaction. */
public interface MarketingFormCreationCoordinator {
    MarketingFormVO createInCurrentTransaction(
            AuthenticatedCaller caller, MarketingFormCreateRequest request);
}
