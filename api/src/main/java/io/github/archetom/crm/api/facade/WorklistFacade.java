package io.github.archetom.crm.api.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.response.WorklistPageResponse;
import io.github.archetom.crm.api.dto.request.WorklistAcknowledgementRequest;

import java.time.LocalDate;

public interface WorklistFacade {
    Result<WorklistPageResponse> query(AuthenticatedCaller caller, LocalDate date, String cursor, Integer size);
    Result<Void> acknowledge(AuthenticatedCaller caller, WorklistAcknowledgementRequest request);
}
