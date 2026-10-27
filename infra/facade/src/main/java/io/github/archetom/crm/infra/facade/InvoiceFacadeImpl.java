package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.InvoiceBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.InvoiceBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceBatchResetRequest;
import io.github.archetom.crm.api.dto.request.InvoiceCreateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceIssueRequest;
import io.github.archetom.crm.api.dto.request.InvoiceOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceProfileRequest;
import io.github.archetom.crm.api.dto.request.InvoiceProfileUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceQueryRequest;
import io.github.archetom.crm.api.dto.request.InvoiceUpdateRequest;
import io.github.archetom.crm.api.dto.response.InvoiceProfileResponse;
import io.github.archetom.crm.api.dto.response.InvoiceResponse;
import io.github.archetom.crm.api.facade.InvoiceFacade;
import io.github.archetom.crm.application.service.InvoiceCommandService;
import io.github.archetom.crm.application.service.InvoiceQueryService;
import io.github.archetom.crm.application.vo.InvoiceVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceFacadeImpl implements InvoiceFacade {
    private final InvoiceCommandService commandService;
    private final InvoiceQueryService queryService;
    private final FileFieldResponseEnricher fileFieldResponseEnricher;

    @Override
    public Result<Pager<InvoiceResponse>> query(
            AuthenticatedCaller caller, InvoiceQueryRequest request) {
        return ResultUtil.map(queryService.query(caller, request), source -> page(caller, source));
    }

    @Override
    public Result<InvoiceResponse> get(AuthenticatedCaller caller, Long invoiceId) {
        return ResultUtil.map(queryService.get(caller, invoiceId),
                value -> response(caller, value));
    }

    @Override
    public Result<InvoiceResponse> create(
            AuthenticatedCaller caller, InvoiceCreateRequest request) {
        return ResultUtil.map(commandService.create(caller, request),
                value -> response(caller, value));
    }

    @Override
    public Result<InvoiceResponse> update(
            AuthenticatedCaller caller, Long invoiceId, InvoiceUpdateRequest request) {
        return ResultUtil.map(commandService.update(caller, invoiceId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<InvoiceResponse> updateOwner(
            AuthenticatedCaller caller, Long invoiceId,
            InvoiceOwnerUpdateRequest request) {
        return ResultUtil.map(commandService.updateOwner(caller, invoiceId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<Void> updateOwners(
            AuthenticatedCaller caller, InvoiceBatchOwnerUpdateRequest request) {
        return commandService.updateOwners(caller, request);
    }

    @Override
    public Result<InvoiceResponse> issue(
            AuthenticatedCaller caller, Long invoiceId, InvoiceIssueRequest request) {
        return ResultUtil.map(commandService.issue(caller, invoiceId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<InvoiceResponse> reset(
            AuthenticatedCaller caller, Long invoiceId, Long version) {
        return ResultUtil.map(commandService.reset(caller, invoiceId, version),
                value -> response(caller, value));
    }

    @Override
    public Result<Void> resetBatch(
            AuthenticatedCaller caller, InvoiceBatchResetRequest request) {
        return commandService.resetBatch(caller, request);
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long invoiceId, Long version) {
        return commandService.delete(caller, invoiceId, version);
    }

    @Override
    public Result<Void> deleteBatch(
            AuthenticatedCaller caller, InvoiceBatchDeleteRequest request) {
        return commandService.deleteBatch(caller, request);
    }

    @Override
    public Result<List<InvoiceProfileResponse>> profiles(
            AuthenticatedCaller caller, Long customerId) {
        return ResultUtil.map(queryService.profiles(caller, customerId), values -> values.stream()
                .map(InvoiceResponseAssembler::toResponse).toList());
    }

    @Override
    public Result<InvoiceProfileResponse> createProfile(
            AuthenticatedCaller caller, InvoiceProfileRequest request) {
        return ResultUtil.map(commandService.createProfile(caller, request),
                InvoiceResponseAssembler::toResponse);
    }

    @Override
    public Result<InvoiceProfileResponse> updateProfile(
            AuthenticatedCaller caller, Long profileId, InvoiceProfileUpdateRequest request) {
        return ResultUtil.map(commandService.updateProfile(caller, profileId, request),
                InvoiceResponseAssembler::toResponse);
    }

    @Override
    public Result<Void> deleteProfile(
            AuthenticatedCaller caller, Long profileId, Long version) {
        return commandService.deleteProfile(caller, profileId, version);
    }

    private InvoiceResponse response(AuthenticatedCaller caller, InvoiceVO value) {
        return response(value, fileFieldResponseEnricher.crm(caller, "invoice", value.id()));
    }

    private static InvoiceResponse response(
            InvoiceVO value, java.util.Map<String, java.util.List<io.github.archetom.crm.api.dto.response.AttachmentResponse>> fileFields) {
        return InvoiceResponseAssembler.toResponse(value).setFileFields(fileFields);
    }

    /** One FILE-value read for the whole projection instead of one per row. */
    private java.util.List<InvoiceResponse> responses(
            AuthenticatedCaller caller, java.util.List<InvoiceVO> values) {
        var fileFields = fileFieldResponseEnricher.crm(
                caller, "invoice", values.stream().map(value -> value.id()).toList());
        return values.stream()
                .map(value -> response(value, fileFields.getOrDefault(value.id(), java.util.Map.of())))
                .toList();
    }

    private Pager<InvoiceResponse> page(AuthenticatedCaller caller, Pager<InvoiceVO> source) {
        Pager<InvoiceResponse> target = PageUtil.copy(source);
        target.setMeta(null);
        target.setObjectList(responses(caller, source.getObjectList()));
        return target;
    }
}
