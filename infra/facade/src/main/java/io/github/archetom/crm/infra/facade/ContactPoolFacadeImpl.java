package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContactPoolAssignRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolCreateRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolContactBatchRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolContactQueryRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolTransferRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolUpdateRequest;
import io.github.archetom.crm.api.dto.response.ContactPoolResponse;
import io.github.archetom.crm.api.dto.response.ContactResponse;
import io.github.archetom.crm.api.facade.ContactPoolFacade;
import io.github.archetom.crm.application.service.ContactPoolService;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContactPoolFacadeImpl implements ContactPoolFacade {

    private final ContactPoolService service;
    private final FileFieldResponseEnricher fileFieldResponseEnricher;

    @Override
    public Result<Pager<ContactPoolResponse>> query(
            AuthenticatedCaller caller, Integer page, Integer size) {
        return ResultUtil.map(service.query(caller, page, size), source -> {
            Pager<ContactPoolResponse> target = PageUtil.copy(source);
            target.setObjectList(source.getObjectList().stream()
                    .map(ContactPoolResponseAssembler::toResponse).toList());
            return target;
        });
    }

    @Override
    public Result<List<ContactPoolResponse>> catalog(AuthenticatedCaller caller) {
        return ResultUtil.map(service.catalog(caller), values -> values.stream()
                .map(ContactPoolResponseAssembler::toResponse).toList());
    }

    @Override
    public Result<ContactPoolResponse> get(AuthenticatedCaller caller, Long poolId) {
        return ResultUtil.map(service.get(caller, poolId), ContactPoolResponseAssembler::toResponse);
    }

    @Override
    public Result<ContactPoolResponse> create(
            AuthenticatedCaller caller, ContactPoolCreateRequest request) {
        return ResultUtil.map(service.create(caller, request), ContactPoolResponseAssembler::toResponse);
    }

    @Override
    public Result<ContactPoolResponse> update(
            AuthenticatedCaller caller, Long poolId, ContactPoolUpdateRequest request) {
        return ResultUtil.map(service.update(caller, poolId, request),
                ContactPoolResponseAssembler::toResponse);
    }

    @Override
    public Result<ContactPoolResponse> updateStatus(
            AuthenticatedCaller caller, Long poolId, ContactPoolStatusUpdateRequest request) {
        return ResultUtil.map(service.updateStatus(caller, poolId, request),
                ContactPoolResponseAssembler::toResponse);
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long poolId, Long version) {
        return service.delete(caller, poolId, version);
    }

    @Override
    public Result<Void> transfer(
            AuthenticatedCaller caller, Long poolId, ContactPoolTransferRequest request) {
        return service.transfer(caller, poolId, request);
    }

    @Override
    public Result<Pager<ContactResponse>> contacts(
            AuthenticatedCaller caller, Long poolId, ContactPoolContactQueryRequest request) {
        return ResultUtil.map(service.contacts(caller, poolId, request), source -> {
            Pager<ContactResponse> target = PageUtil.copy(source);
            target.setObjectList(responses(caller, source.getObjectList()));
            return target;
        });
    }

    @Override
    public Result<ContactResponse> contact(
            AuthenticatedCaller caller, Long poolId, Long contactId) {
        return ResultUtil.map(service.contact(caller, poolId, contactId),
                value -> response(caller, value));
    }

    @Override
    public Result<List<ContactResponse>> release(
            AuthenticatedCaller caller, Long poolId, ContactPoolContactBatchRequest request) {
        return contacts(caller, service.release(caller, poolId, request));
    }

    @Override
    public Result<List<ContactResponse>> claim(
            AuthenticatedCaller caller, Long poolId, ContactPoolContactBatchRequest request) {
        return contacts(caller, service.claim(caller, poolId, request));
    }

    @Override
    public Result<List<ContactResponse>> assign(
            AuthenticatedCaller caller, Long poolId, ContactPoolAssignRequest request) {
        return contacts(caller, service.assign(caller, poolId, request));
    }

    @Override
    public Result<Void> deleteContacts(
            AuthenticatedCaller caller, Long poolId, ContactPoolContactBatchRequest request) {
        return service.deleteContacts(caller, poolId, request);
    }

    private ContactResponse response(AuthenticatedCaller caller, io.github.archetom.crm.application.vo.ContactVO value) {
        return response(value, fileFieldResponseEnricher.crm(caller, "contact", value.id()));
    }

    private static ContactResponse response(
            io.github.archetom.crm.application.vo.ContactVO value, java.util.Map<String, java.util.List<io.github.archetom.crm.api.dto.response.AttachmentResponse>> fileFields) {
        return ContactResponseAssembler.toResponse(value).setFileFields(fileFields);
    }

    /** One FILE-value read for the whole projection instead of one per row. */
    private java.util.List<ContactResponse> responses(
            AuthenticatedCaller caller, java.util.List<io.github.archetom.crm.application.vo.ContactVO> values) {
        var fileFields = fileFieldResponseEnricher.crm(
                caller, "contact", values.stream().map(value -> value.id()).toList());
        return values.stream()
                .map(value -> response(value, fileFields.getOrDefault(value.id(), java.util.Map.of())))
                .toList();
    }

    private Result<List<ContactResponse>> contacts(AuthenticatedCaller caller,
            Result<List<io.github.archetom.crm.application.vo.ContactVO>> source) {
        return ResultUtil.map(source, values -> responses(caller, values));
    }
}
