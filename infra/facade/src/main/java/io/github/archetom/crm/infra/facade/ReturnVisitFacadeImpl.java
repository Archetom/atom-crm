package io.github.archetom.crm.infra.facade;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReturnVisitCreateRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitQueryRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitUpdateRequest;
import io.github.archetom.crm.api.dto.response.ReturnVisitResponse;
import io.github.archetom.crm.api.dto.response.ReturnVisitOptionResponse;
import io.github.archetom.crm.api.facade.ReturnVisitFacade;
import io.github.archetom.crm.application.service.ReturnVisitCommandService;
import io.github.archetom.crm.application.service.ReturnVisitQueryService;
import io.github.archetom.crm.application.vo.ReturnVisitVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReturnVisitFacadeImpl implements ReturnVisitFacade {
    private final ReturnVisitCommandService commandService;
    private final ReturnVisitQueryService queryService;
    private final FileFieldResponseEnricher fileFieldResponseEnricher;
    @Override public Result<Pager<ReturnVisitResponse>> query(AuthenticatedCaller caller, ReturnVisitQueryRequest request) {
        return ResultUtil.map(queryService.query(caller, request), source -> page(caller, source));
    }
    @Override public Result<List<ReturnVisitOptionResponse>> options(
            AuthenticatedCaller caller, String keyword, Integer limit) {
        return ResultUtil.map(queryService.options(caller, keyword, limit), values -> values.stream()
                .map(value -> new ReturnVisitOptionResponse().setId(value.id())
                        .setVisitNumber(value.visitNumber()).setVisitTime(value.visitTime())).toList());
    }
    @Override public Result<ReturnVisitResponse> get(AuthenticatedCaller caller, Long id) {
        return ResultUtil.map(queryService.get(caller, id), value -> response(caller, value));
    }
    @Override public Result<ReturnVisitResponse> create(AuthenticatedCaller caller, ReturnVisitCreateRequest request) {
        return ResultUtil.map(commandService.create(caller, request), value -> response(caller, value));
    }
    @Override public Result<ReturnVisitResponse> update(AuthenticatedCaller caller, Long id, ReturnVisitUpdateRequest request) {
        return ResultUtil.map(commandService.update(caller, id, request), value -> response(caller, value));
    }
    @Override public Result<ReturnVisitResponse> updateOwner(AuthenticatedCaller caller, Long id, ReturnVisitOwnerUpdateRequest request) {
        return ResultUtil.map(commandService.updateOwner(caller, id, request), value -> response(caller, value));
    }
    @Override public Result<Void> delete(AuthenticatedCaller caller, Long id, Long version) { return commandService.delete(caller, id, version); }
    private ReturnVisitResponse response(AuthenticatedCaller caller, ReturnVisitVO value) {
        return response(value, fileFieldResponseEnricher.crm(caller, "return-visit", value.id()));
    }

    private static ReturnVisitResponse response(
            ReturnVisitVO value, java.util.Map<String, java.util.List<io.github.archetom.crm.api.dto.response.AttachmentResponse>> fileFields) {
        return ReturnVisitResponseAssembler.toResponse(value).setFileFields(fileFields);
    }

    /** One FILE-value read for the whole projection instead of one per row. */
    private java.util.List<ReturnVisitResponse> responses(
            AuthenticatedCaller caller, java.util.List<ReturnVisitVO> values) {
        var fileFields = fileFieldResponseEnricher.crm(
                caller, "return-visit", values.stream().map(value -> value.id()).toList());
        return values.stream()
                .map(value -> response(value, fileFields.getOrDefault(value.id(), java.util.Map.of())))
                .toList();
    }
    private Pager<ReturnVisitResponse> page(AuthenticatedCaller caller, Pager<ReturnVisitVO> source) {
        Pager<ReturnVisitResponse> result = PageUtil.copy(source); result.setMeta(null);
        result.setObjectList(responses(caller, source.getObjectList())); return result;
    }
}
