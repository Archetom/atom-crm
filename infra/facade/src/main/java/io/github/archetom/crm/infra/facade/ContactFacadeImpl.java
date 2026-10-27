package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContactBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.ContactBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactBatchTagRequest;
import io.github.archetom.crm.api.dto.request.ContactCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactCreateRequest;
import io.github.archetom.crm.api.dto.request.ContactDuplicateCheckRequest;
import io.github.archetom.crm.api.dto.request.ContactOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactParentUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactQueryRequest;
import io.github.archetom.crm.api.dto.request.ContactStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactStateUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactTagsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactUpdateRequest;
import io.github.archetom.crm.api.dto.response.ContactCollaboratorBaselineResponse;
import io.github.archetom.crm.api.dto.response.ContactDuplicateResponse;
import io.github.archetom.crm.api.dto.response.ContactHierarchyResponse;
import io.github.archetom.crm.api.dto.response.ContactResponse;
import io.github.archetom.crm.api.dto.response.NearbyContactResponse;
import io.github.archetom.crm.api.facade.ContactFacade;
import io.github.archetom.crm.application.service.ContactCommandService;
import io.github.archetom.crm.application.service.ContactQueryService;
import io.github.archetom.crm.application.vo.ContactVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContactFacadeImpl implements ContactFacade {

    private final ContactCommandService commandService;
    private final ContactQueryService queryService;
    private final FileFieldResponseEnricher fileFieldResponseEnricher;

    @Override
    public Result<Pager<ContactResponse>> query(
            AuthenticatedCaller caller, ContactQueryRequest request) {
        return ResultUtil.map(queryService.query(caller, request), source -> page(caller, source));
    }

    @Override
    public Result<ContactResponse> get(AuthenticatedCaller caller, Long contactId) {
        return ResultUtil.map(queryService.get(caller, contactId), value -> response(caller, value));
    }

    @Override
    public Result<ContactResponse> create(
            AuthenticatedCaller caller, ContactCreateRequest request) {
        return ResultUtil.map(commandService.create(caller, request), value -> response(caller, value));
    }

    @Override
    public Result<ContactResponse> update(
            AuthenticatedCaller caller, Long contactId, ContactUpdateRequest request) {
        return ResultUtil.map(commandService.update(caller, contactId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long contactId, Long version) {
        return commandService.delete(caller, contactId, version);
    }

    @Override
    public Result<Void> deleteBatch(
            AuthenticatedCaller caller, ContactBatchDeleteRequest request) {
        return commandService.deleteBatch(caller, request);
    }

    @Override
    public Result<ContactResponse> updateState(
            AuthenticatedCaller caller, Long contactId, ContactStateUpdateRequest request) {
        return ResultUtil.map(commandService.updateState(caller, contactId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<ContactResponse> updateOwner(
            AuthenticatedCaller caller, Long contactId, ContactOwnerUpdateRequest request) {
        return ResultUtil.map(commandService.updateOwner(caller, contactId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<Void> updateOwners(
            AuthenticatedCaller caller, ContactBatchOwnerUpdateRequest request) {
        return commandService.updateOwners(caller, request);
    }

    @Override
    public Result<ContactResponse> replaceTags(
            AuthenticatedCaller caller, Long contactId, ContactTagsUpdateRequest request) {
        return ResultUtil.map(commandService.replaceTags(caller, contactId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<List<ContactResponse>> changeTags(
            AuthenticatedCaller caller, ContactBatchTagRequest request) {
        return ResultUtil.map(commandService.changeTags(caller, request), values ->
                responses(caller, values));
    }

    @Override
    public Result<ContactCollaboratorBaselineResponse> collaborators(
            AuthenticatedCaller caller, Long contactId) {
        return ResultUtil.map(queryService.collaborators(caller, contactId),
                ContactResponseAssembler::toResponse);
    }

    @Override
    public Result<ContactResponse> replaceCollaborators(
            AuthenticatedCaller caller, Long contactId,
            ContactCollaboratorsUpdateRequest request) {
        return ResultUtil.map(commandService.replaceCollaborators(caller, contactId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<Void> exitCollaborator(
            AuthenticatedCaller caller, Long contactId, Long version) {
        return commandService.exitCollaborator(caller, contactId, version);
    }

    @Override
    public Result<ContactResponse> updateParent(
            AuthenticatedCaller caller, Long contactId, ContactParentUpdateRequest request) {
        return ResultUtil.map(commandService.updateParent(caller, contactId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<ContactHierarchyResponse> hierarchy(
            AuthenticatedCaller caller, Long contactId) {
        return ResultUtil.map(queryService.hierarchy(caller, contactId), value ->
                new ContactHierarchyResponse().setRequestedContactId(value.requestedContactId())
                        .setContacts(responses(caller, value.contacts())));
    }

    @Override
    public Result<Void> setStarred(
            AuthenticatedCaller caller, Long contactId, ContactStarUpdateRequest request) {
        return commandService.setStarred(caller, contactId, request);
    }

    @Override
    public Result<ContactDuplicateResponse> duplicateCheck(
            AuthenticatedCaller caller, ContactDuplicateCheckRequest request) {
        return ResultUtil.map(queryService.duplicateCheck(caller, request), value ->
                new ContactDuplicateResponse().setDuplicate(value));
    }

    @Override
    public Result<Pager<NearbyContactResponse>> nearby(
            AuthenticatedCaller caller, BigDecimal longitude, BigDecimal latitude,
            Integer radiusMeters, Integer page, Integer size) {
        return ResultUtil.map(queryService.nearby(caller, longitude, latitude,
                radiusMeters, page, size), source -> nearbyPage(caller, source));
    }

    @Override
    public Result<List<ContactResponse>> summaries(
            AuthenticatedCaller caller, List<Long> ids) {
        return ResultUtil.map(queryService.summaries(caller, ids), values ->
                responses(caller, values));
    }

    @Override
    public Result<List<String>> tags(AuthenticatedCaller caller) {
        return queryService.tags(caller);
    }

    private ContactResponse response(AuthenticatedCaller caller, ContactVO value) {
        return response(value, fileFieldResponseEnricher.crm(caller, "contact", value.id()));
    }

    private static ContactResponse response(
            ContactVO value, java.util.Map<String, java.util.List<io.github.archetom.crm.api.dto.response.AttachmentResponse>> fileFields) {
        return ContactResponseAssembler.toResponse(value).setFileFields(fileFields);
    }

    /** One FILE-value read for the whole projection instead of one per row. */
    private java.util.List<ContactResponse> responses(
            AuthenticatedCaller caller, java.util.List<ContactVO> values) {
        var fileFields = fileFieldResponseEnricher.crm(
                caller, "contact", values.stream().map(value -> value.id()).toList());
        return values.stream()
                .map(value -> response(value, fileFields.getOrDefault(value.id(), java.util.Map.of())))
                .toList();
    }

    private Pager<ContactResponse> page(AuthenticatedCaller caller, Pager<ContactVO> source) {
        Pager<ContactResponse> target = PageUtil.copy(source);
        target.setMeta(null);
        target.setObjectList(responses(caller, source.getObjectList()));
        return target;
    }

    private Pager<NearbyContactResponse> nearbyPage(AuthenticatedCaller caller,
            Pager<io.github.archetom.crm.application.vo.NearbyContactVO> source) {
        Pager<NearbyContactResponse> target = PageUtil.copy(source);
        target.setMeta(null);
        java.util.List<ContactResponse> rows = responses(caller, source.getObjectList().stream()
                .map(io.github.archetom.crm.application.vo.NearbyContactVO::contact).toList());
        java.util.List<NearbyContactResponse> nearby = new java.util.ArrayList<>();
        for (int index = 0; index < source.getObjectList().size(); index++) {
            nearby.add(new NearbyContactResponse()
                    .setContact(rows.get(index))
                    .setDistanceMeters(source.getObjectList().get(index).distanceMeters()));
        }
        target.setObjectList(java.util.List.copyOf(nearby));
        return target;
    }
}
