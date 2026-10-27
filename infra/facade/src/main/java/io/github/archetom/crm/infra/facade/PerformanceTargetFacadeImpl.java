package io.github.archetom.crm.infra.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.PerformanceTargetCreateRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetImportRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetQueryRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetUpdateRequest;
import io.github.archetom.crm.api.dto.response.PerformanceTargetImportResponse;
import io.github.archetom.crm.api.dto.response.PerformanceTargetListResponse;
import io.github.archetom.crm.api.dto.response.PerformanceTargetResponse;
import io.github.archetom.crm.api.facade.PerformanceTargetFacade;
import io.github.archetom.crm.application.service.PerformanceTargetService;
import io.github.archetom.crm.application.vo.PerformanceTargetVO;
import io.github.archetom.crm.shared.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Maps the application target catalog to named HTTP responses. */
@Service
@RequiredArgsConstructor
public class PerformanceTargetFacadeImpl implements PerformanceTargetFacade {

    private final PerformanceTargetService service;

    @Override
    public Result<PerformanceTargetListResponse> query(
            AuthenticatedCaller caller, PerformanceTargetQueryRequest request) {
        return ResultUtil.map(service.query(caller, request), values ->
                new PerformanceTargetListResponse().setTargets(values.stream()
                        .map(this::response).toList()));
    }

    @Override
    public Result<PerformanceTargetResponse> create(
            AuthenticatedCaller caller, PerformanceTargetCreateRequest request) {
        return ResultUtil.map(service.create(caller, request), this::response);
    }

    @Override
    public Result<PerformanceTargetImportResponse> importTargets(
            AuthenticatedCaller caller, PerformanceTargetImportRequest request) {
        return ResultUtil.map(service.importTargets(caller, request),
                value -> new PerformanceTargetImportResponse(value.importedCount()));
    }

    @Override
    public Result<PerformanceTargetResponse> update(
            AuthenticatedCaller caller, Long targetId, PerformanceTargetUpdateRequest request) {
        return ResultUtil.map(service.update(caller, targetId, request), this::response);
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long targetId, Long version) {
        return service.delete(caller, targetId, version);
    }

    private PerformanceTargetResponse response(PerformanceTargetVO value) {
        return new PerformanceTargetResponse()
                .setId(value.id())
                .setMemberId(value.memberId())
                .setMetric(value.metric())
                .setTargetYear(value.targetYear())
                .setTargetMonth(value.targetMonth())
                .setAmount(value.amount())
                .setVersion(value.version())
                .setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }
}
