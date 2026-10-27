package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReceivableBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.ReceivableBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableQueryRequest;
import io.github.archetom.crm.api.dto.request.ReceivableUpdateRequest;
import io.github.archetom.crm.api.dto.response.ReceivableListSummaryResponse;
import io.github.archetom.crm.api.dto.response.ReceivableResponse;
import io.github.archetom.crm.api.facade.ReceivableFacade;
import io.github.archetom.crm.application.service.ReceivableCommandService;
import io.github.archetom.crm.application.service.ReceivableQueryService;
import io.github.archetom.crm.application.vo.ReceivableVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReceivableFacadeImpl implements ReceivableFacade {
    private final ReceivableCommandService commandService;
    private final ReceivableQueryService queryService;
    private final FileFieldResponseEnricher fileFieldResponseEnricher;

    @Override
    public Result<Pager<ReceivableResponse>> query(
            AuthenticatedCaller caller, ReceivableQueryRequest request) {
        return ResultUtil.map(queryService.query(caller, request), source -> page(caller, source));
    }

    @Override
    public Result<ReceivableListSummaryResponse> summary(
            AuthenticatedCaller caller, ReceivableQueryRequest request) {
        return ResultUtil.map(queryService.summary(caller, request), value ->
                new ReceivableListSummaryResponse().setCount(value.count())
                        .setTotalAmount(value.totalAmount()));
    }

    @Override
    public Result<ReceivableResponse> get(AuthenticatedCaller caller, Long receivableId) {
        return ResultUtil.map(queryService.get(caller, receivableId), value -> response(caller, value));
    }

    @Override
    public Result<ReceivableResponse> create(AuthenticatedCaller caller, ReceivableCreateRequest request) {
        return ResultUtil.map(commandService.create(caller, request), value -> response(caller, value));
    }

    @Override
    public Result<ReceivableResponse> update(
            AuthenticatedCaller caller, Long receivableId, ReceivableUpdateRequest request) {
        return ResultUtil.map(commandService.update(caller, receivableId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<ReceivableResponse> updateOwner(
            AuthenticatedCaller caller, Long receivableId, ReceivableOwnerUpdateRequest request) {
        return ResultUtil.map(commandService.updateOwner(caller, receivableId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<Void> updateOwners(
            AuthenticatedCaller caller, ReceivableBatchOwnerUpdateRequest request) {
        return commandService.updateOwners(caller, request);
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long receivableId, Long version) {
        return commandService.delete(caller, receivableId, version);
    }

    @Override
    public Result<Void> deleteBatch(
            AuthenticatedCaller caller, ReceivableBatchDeleteRequest request) {
        return commandService.deleteBatch(caller, request);
    }

    private ReceivableResponse response(AuthenticatedCaller caller, ReceivableVO value) {
        return response(value, fileFieldResponseEnricher.crm(caller, "receivable", value.id()));
    }

    private static ReceivableResponse response(
            ReceivableVO value, java.util.Map<String, java.util.List<io.github.archetom.crm.api.dto.response.AttachmentResponse>> fileFields) {
        return ReceivableResponseAssembler.toResponse(value).setFileFields(fileFields);
    }

    /** One FILE-value read for the whole projection instead of one per row. */
    private java.util.List<ReceivableResponse> responses(
            AuthenticatedCaller caller, java.util.List<ReceivableVO> values) {
        var fileFields = fileFieldResponseEnricher.crm(
                caller, "receivable", values.stream().map(value -> value.id()).toList());
        return values.stream()
                .map(value -> response(value, fileFields.getOrDefault(value.id(), java.util.Map.of())))
                .toList();
    }

    private Pager<ReceivableResponse> page(AuthenticatedCaller caller, Pager<ReceivableVO> source) {
        Pager<ReceivableResponse> target = PageUtil.copy(source);
        target.setMeta(null);
        target.setObjectList(responses(caller, source.getObjectList()));
        return target;
    }
}
