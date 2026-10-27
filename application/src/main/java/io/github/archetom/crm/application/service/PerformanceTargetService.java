package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.PerformanceTargetCreateRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetImportRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetQueryRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetUpdateRequest;
import io.github.archetom.crm.application.vo.PerformanceTargetImportVO;
import io.github.archetom.crm.application.vo.PerformanceTargetVO;

import java.util.List;

/** Application boundary for the tenant-administered member target catalog. */
public interface PerformanceTargetService {
    Result<List<PerformanceTargetVO>> query(
            AuthenticatedCaller caller, PerformanceTargetQueryRequest request);

    Result<PerformanceTargetVO> create(
            AuthenticatedCaller caller, PerformanceTargetCreateRequest request);

    Result<PerformanceTargetImportVO> importTargets(
            AuthenticatedCaller caller, PerformanceTargetImportRequest request);

    Result<PerformanceTargetVO> update(
            AuthenticatedCaller caller, Long targetId, PerformanceTargetUpdateRequest request);

    Result<Void> delete(AuthenticatedCaller caller, Long targetId, Long version);
}
