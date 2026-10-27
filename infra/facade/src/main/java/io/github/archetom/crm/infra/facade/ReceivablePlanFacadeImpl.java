package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReceivablePlanBatchCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanQueryRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanUpdateRequest;
import io.github.archetom.crm.api.dto.response.ReceivablePlanResponse;
import io.github.archetom.crm.api.dto.response.ReceivablePlanListSummaryResponse;
import io.github.archetom.crm.api.facade.ReceivablePlanFacade;
import io.github.archetom.crm.application.service.ReceivablePlanCommandService;
import io.github.archetom.crm.application.service.ReceivablePlanQueryService;
import io.github.archetom.crm.application.vo.ReceivablePlanVO;
import io.github.archetom.crm.application.vo.ReceivablePlanListSummaryVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReceivablePlanFacadeImpl implements ReceivablePlanFacade {
    private final ReceivablePlanCommandService commandService;
    private final ReceivablePlanQueryService queryService;
    private final FileFieldResponseEnricher fileFieldResponseEnricher;

    @Override
    public Result<Pager<ReceivablePlanResponse>> query(
            AuthenticatedCaller caller, ReceivablePlanQueryRequest request) {
        return ResultUtil.map(queryService.query(caller, request), source -> page(caller, source));
    }

    @Override
    public Result<ReceivablePlanListSummaryResponse> summary(
            AuthenticatedCaller caller, ReceivablePlanQueryRequest request) {
        return ResultUtil.map(queryService.summary(caller, request), this::summary);
    }

    @Override
    public Result<ReceivablePlanResponse> get(AuthenticatedCaller caller, Long planId) {
        return ResultUtil.map(queryService.get(caller, planId), value -> response(caller, value));
    }

    @Override
    public Result<List<ReceivablePlanResponse>> available(AuthenticatedCaller caller, Long contractId) {
        return ResultUtil.map(queryService.available(caller, contractId), values -> responses(caller, values));
    }

    @Override
    public Result<ReceivablePlanResponse> create(
            AuthenticatedCaller caller, ReceivablePlanCreateRequest request) {
        return ResultUtil.map(commandService.create(caller, request), value -> response(caller, value));
    }

    @Override
    public Result<List<ReceivablePlanResponse>> createBatch(
            AuthenticatedCaller caller, ReceivablePlanBatchCreateRequest request) {
        return ResultUtil.map(commandService.createBatch(caller, request), values -> responses(caller, values));
    }

    @Override
    public Result<ReceivablePlanResponse> update(
            AuthenticatedCaller caller, Long planId, ReceivablePlanUpdateRequest request) {
        return ResultUtil.map(commandService.update(caller, planId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long planId, Long version) {
        return commandService.delete(caller, planId, version);
    }

    private ReceivablePlanResponse response(AuthenticatedCaller caller, ReceivablePlanVO value) {
        return response(value, fileFieldResponseEnricher.crm(caller, "receivable-plan", value.id()));
    }

    private static ReceivablePlanResponse response(
            ReceivablePlanVO value, java.util.Map<String, java.util.List<io.github.archetom.crm.api.dto.response.AttachmentResponse>> fileFields) {
        return ReceivableResponseAssembler.toResponse(value).setFileFields(fileFields);
    }

    /** One FILE-value read for the whole projection instead of one per row. */
    private java.util.List<ReceivablePlanResponse> responses(
            AuthenticatedCaller caller, java.util.List<ReceivablePlanVO> values) {
        var fileFields = fileFieldResponseEnricher.crm(
                caller, "receivable-plan", values.stream().map(value -> value.id()).toList());
        return values.stream()
                .map(value -> response(value, fileFields.getOrDefault(value.id(), java.util.Map.of())))
                .toList();
    }

    private ReceivablePlanListSummaryResponse summary(ReceivablePlanListSummaryVO value) {
        return new ReceivablePlanListSummaryResponse().setCount(value.count())
                .setPlannedAmount(value.plannedAmount()).setReceivedAmount(value.receivedAmount())
                .setRemainingAmount(value.remainingAmount());
    }

    private Pager<ReceivablePlanResponse> page(AuthenticatedCaller caller, Pager<ReceivablePlanVO> source) {
        Pager<ReceivablePlanResponse> target = PageUtil.copy(source);
        target.setMeta(null);
        target.setObjectList(responses(caller, source.getObjectList()));
        return target;
    }
}
