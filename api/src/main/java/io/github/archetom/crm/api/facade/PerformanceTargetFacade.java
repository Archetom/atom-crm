package io.github.archetom.crm.api.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.PerformanceTargetCreateRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetImportRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetQueryRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetUpdateRequest;
import io.github.archetom.crm.api.dto.response.PerformanceTargetImportResponse;
import io.github.archetom.crm.api.dto.response.PerformanceTargetListResponse;
import io.github.archetom.crm.api.dto.response.PerformanceTargetResponse;

/** Management boundary for the server-owned performance target catalog. */
public interface PerformanceTargetFacade {
    Result<PerformanceTargetListResponse> query(
            AuthenticatedCaller caller, PerformanceTargetQueryRequest request);

    Result<PerformanceTargetResponse> create(
            AuthenticatedCaller caller, PerformanceTargetCreateRequest request);

    Result<PerformanceTargetImportResponse> importTargets(
            AuthenticatedCaller caller, PerformanceTargetImportRequest request);

    Result<PerformanceTargetResponse> update(
            AuthenticatedCaller caller, Long targetId, PerformanceTargetUpdateRequest request);

    Result<Void> delete(AuthenticatedCaller caller, Long targetId, Long version);
}
