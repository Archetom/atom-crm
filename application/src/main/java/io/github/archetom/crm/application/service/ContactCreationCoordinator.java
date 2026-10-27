package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContactCreateRequest;
import io.github.archetom.crm.application.vo.ContactVO;

/** Reuses contact creation inside an already-authorized composite command transaction. */
public interface ContactCreationCoordinator {
    ContactVO createInCurrentTransaction(AuthenticatedCaller caller, ContactCreateRequest request);
}
