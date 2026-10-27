package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetBatchUpsertRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetCreateRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetImportRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetQueryRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetUpdateRequest;
import io.github.archetom.crm.application.vo.PerformanceTargetImportVO;
import io.github.archetom.crm.application.vo.DepartmentPerformanceTargetVO;

import java.util.List;

/** Application boundary for server-owned department performance targets. */
public interface DepartmentPerformanceTargetService {
    Result<List<DepartmentPerformanceTargetVO>> query(
            AuthenticatedCaller caller, DepartmentPerformanceTargetQueryRequest request);

    Result<DepartmentPerformanceTargetVO> create(
            AuthenticatedCaller caller, DepartmentPerformanceTargetCreateRequest request);

    Result<DepartmentPerformanceTargetVO> update(
            AuthenticatedCaller caller, Long targetId, DepartmentPerformanceTargetUpdateRequest request);

    Result<List<DepartmentPerformanceTargetVO>> batchUpsert(
            AuthenticatedCaller caller, DepartmentPerformanceTargetBatchUpsertRequest request);

    Result<PerformanceTargetImportVO> importTargets(
            AuthenticatedCaller caller, DepartmentPerformanceTargetImportRequest request);

    Result<Void> delete(AuthenticatedCaller caller, Long targetId, Long version);
}
