package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorklistAcknowledgementRequest;

public interface WorklistAcknowledgementService {
    Result<Void> acknowledge(AuthenticatedCaller caller, WorklistAcknowledgementRequest request);
}
