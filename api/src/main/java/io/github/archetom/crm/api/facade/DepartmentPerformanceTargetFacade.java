package io.github.archetom.crm.api.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetBatchUpsertRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetCreateRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetImportRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetQueryRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetUpdateRequest;
import io.github.archetom.crm.api.dto.response.DepartmentPerformanceTargetListResponse;
import io.github.archetom.crm.api.dto.response.DepartmentPerformanceTargetResponse;
import io.github.archetom.crm.api.dto.response.PerformanceTargetImportResponse;

/** Management boundary for the server-owned department performance target catalog. */
public interface DepartmentPerformanceTargetFacade {
    Result<DepartmentPerformanceTargetListResponse> query(
            AuthenticatedCaller caller, DepartmentPerformanceTargetQueryRequest request);

    Result<DepartmentPerformanceTargetResponse> create(
            AuthenticatedCaller caller, DepartmentPerformanceTargetCreateRequest request);

    Result<DepartmentPerformanceTargetResponse> update(
            AuthenticatedCaller caller, Long targetId, DepartmentPerformanceTargetUpdateRequest request);

    Result<DepartmentPerformanceTargetListResponse> batchUpsert(
            AuthenticatedCaller caller, DepartmentPerformanceTargetBatchUpsertRequest request);

    Result<PerformanceTargetImportResponse> importTargets(
            AuthenticatedCaller caller, DepartmentPerformanceTargetImportRequest request);

    Result<Void> delete(AuthenticatedCaller caller, Long targetId, Long version);
}
