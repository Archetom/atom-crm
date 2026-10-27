package io.github.archetom.crm.infra.facade;

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
import io.github.archetom.crm.api.facade.DepartmentPerformanceTargetFacade;
import io.github.archetom.crm.application.service.DepartmentPerformanceTargetService;
import io.github.archetom.crm.application.vo.DepartmentPerformanceTargetVO;
import io.github.archetom.crm.shared.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Maps the department target catalog to named HTTP responses. */
@Service
@RequiredArgsConstructor
public class DepartmentPerformanceTargetFacadeImpl implements DepartmentPerformanceTargetFacade {

    private final DepartmentPerformanceTargetService service;

    @Override
    public Result<DepartmentPerformanceTargetListResponse> query(
            AuthenticatedCaller caller, DepartmentPerformanceTargetQueryRequest request) {
        return ResultUtil.map(service.query(caller, request), values ->
                new DepartmentPerformanceTargetListResponse().setTargets(values.stream()
                        .map(this::response).toList()));
    }

    @Override
    public Result<DepartmentPerformanceTargetResponse> create(
            AuthenticatedCaller caller, DepartmentPerformanceTargetCreateRequest request) {
        return ResultUtil.map(service.create(caller, request), this::response);
    }

    @Override
    public Result<DepartmentPerformanceTargetResponse> update(
            AuthenticatedCaller caller, Long targetId, DepartmentPerformanceTargetUpdateRequest request) {
        return ResultUtil.map(service.update(caller, targetId, request), this::response);
    }

    @Override
    public Result<DepartmentPerformanceTargetListResponse> batchUpsert(
            AuthenticatedCaller caller, DepartmentPerformanceTargetBatchUpsertRequest request) {
        return ResultUtil.map(service.batchUpsert(caller, request), values ->
                new DepartmentPerformanceTargetListResponse().setTargets(values.stream()
                        .map(this::response).toList()));
    }

    @Override
    public Result<PerformanceTargetImportResponse> importTargets(
            AuthenticatedCaller caller, DepartmentPerformanceTargetImportRequest request) {
        return ResultUtil.map(service.importTargets(caller, request),
                value -> new PerformanceTargetImportResponse(value.importedCount()));
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long targetId, Long version) {
        return service.delete(caller, targetId, version);
    }

    private DepartmentPerformanceTargetResponse response(DepartmentPerformanceTargetVO value) {
        return new DepartmentPerformanceTargetResponse()
                .setId(value.id())
                .setDepartmentId(value.departmentId())
                .setMetric(value.metric())
                .setTargetYear(value.targetYear())
                .setPeriod(value.period())
                .setPeriodNumber(value.periodNumber())
                .setAmount(value.amount())
                .setVersion(value.version())
                .setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }
}
