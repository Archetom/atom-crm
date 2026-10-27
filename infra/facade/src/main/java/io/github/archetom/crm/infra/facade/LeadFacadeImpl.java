package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.LeadBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadBatchTagRequest;
import io.github.archetom.crm.api.dto.request.LeadCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadConvertRequest;
import io.github.archetom.crm.api.dto.request.LeadCreateRequest;
import io.github.archetom.crm.api.dto.request.LeadDuplicateCheckRequest;
import io.github.archetom.crm.api.dto.request.LeadOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadParentUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadQueryRequest;
import io.github.archetom.crm.api.dto.request.LeadStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadStateUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadTagsUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadUpdateRequest;
import io.github.archetom.crm.api.dto.response.LeadCollaboratorBaselineResponse;
import io.github.archetom.crm.api.dto.response.LeadConversionResponse;
import io.github.archetom.crm.api.dto.response.LeadDuplicateResponse;
import io.github.archetom.crm.api.dto.response.LeadHierarchyResponse;
import io.github.archetom.crm.api.dto.response.LeadResponse;
import io.github.archetom.crm.api.dto.response.NearbyLeadResponse;
import io.github.archetom.crm.api.facade.LeadFacade;
import io.github.archetom.crm.application.service.LeadCommandService;
import io.github.archetom.crm.application.service.LeadQueryService;
import io.github.archetom.crm.application.vo.LeadVO;
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
public class LeadFacadeImpl implements LeadFacade {

    private final LeadCommandService commandService;
    private final LeadQueryService queryService;
    private final FileFieldResponseEnricher fileFieldResponseEnricher;

    @Override
    public Result<Pager<LeadResponse>> query(
            AuthenticatedCaller caller, LeadQueryRequest request) {
        return ResultUtil.map(queryService.query(caller, request), source -> page(caller, source));
    }

    @Override
    public Result<LeadResponse> get(AuthenticatedCaller caller, Long leadId) {
        return ResultUtil.map(queryService.get(caller, leadId), value -> response(caller, value));
    }

    @Override
    public Result<LeadResponse> create(
            AuthenticatedCaller caller, LeadCreateRequest request) {
        return ResultUtil.map(commandService.create(caller, request), value -> response(caller, value));
    }

    @Override
    public Result<LeadConversionResponse> convert(
            AuthenticatedCaller caller, Long leadId, LeadConvertRequest request) {
        return ResultUtil.map(commandService.convert(caller, leadId, request), value ->
                new LeadConversionResponse().setLeadId(value.leadId())
                        .setCustomerId(value.customerId()).setContactId(value.contactId())
                        .setLeadVersion(value.leadVersion()));
    }

    @Override
    public Result<LeadResponse> update(
            AuthenticatedCaller caller, Long leadId, LeadUpdateRequest request) {
        return ResultUtil.map(commandService.update(caller, leadId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long leadId, Long version) {
        return commandService.delete(caller, leadId, version);
    }

    @Override
    public Result<Void> deleteBatch(
            AuthenticatedCaller caller, LeadBatchDeleteRequest request) {
        return commandService.deleteBatch(caller, request);
    }

    @Override
    public Result<LeadResponse> updateState(
            AuthenticatedCaller caller, Long leadId, LeadStateUpdateRequest request) {
        return ResultUtil.map(commandService.updateState(caller, leadId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<LeadResponse> updateOwner(
            AuthenticatedCaller caller, Long leadId, LeadOwnerUpdateRequest request) {
        return ResultUtil.map(commandService.updateOwner(caller, leadId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<Void> updateOwners(
            AuthenticatedCaller caller, LeadBatchOwnerUpdateRequest request) {
        return commandService.updateOwners(caller, request);
    }

    @Override
    public Result<LeadResponse> replaceTags(
            AuthenticatedCaller caller, Long leadId, LeadTagsUpdateRequest request) {
        return ResultUtil.map(commandService.replaceTags(caller, leadId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<List<LeadResponse>> changeTags(
            AuthenticatedCaller caller, LeadBatchTagRequest request) {
        return ResultUtil.map(commandService.changeTags(caller, request), values ->
                responses(caller, values));
    }

    @Override
    public Result<LeadCollaboratorBaselineResponse> collaborators(
            AuthenticatedCaller caller, Long leadId) {
        return ResultUtil.map(queryService.collaborators(caller, leadId),
                LeadResponseAssembler::toResponse);
    }

    @Override
    public Result<LeadResponse> replaceCollaborators(
            AuthenticatedCaller caller, Long leadId,
            LeadCollaboratorsUpdateRequest request) {
        return ResultUtil.map(commandService.replaceCollaborators(caller, leadId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<Void> exitCollaborator(
            AuthenticatedCaller caller, Long leadId, Long version) {
        return commandService.exitCollaborator(caller, leadId, version);
    }

    @Override
    public Result<LeadResponse> updateParent(
            AuthenticatedCaller caller, Long leadId, LeadParentUpdateRequest request) {
        return ResultUtil.map(commandService.updateParent(caller, leadId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<LeadHierarchyResponse> hierarchy(
            AuthenticatedCaller caller, Long leadId) {
        return ResultUtil.map(queryService.hierarchy(caller, leadId), value ->
                new LeadHierarchyResponse().setRequestedLeadId(value.requestedLeadId())
                        .setLeads(responses(caller, value.leads())));
    }

    @Override
    public Result<Void> setStarred(
            AuthenticatedCaller caller, Long leadId, LeadStarUpdateRequest request) {
        return commandService.setStarred(caller, leadId, request);
    }

    @Override
    public Result<LeadDuplicateResponse> duplicateCheck(
            AuthenticatedCaller caller, LeadDuplicateCheckRequest request) {
        return ResultUtil.map(queryService.duplicateCheck(caller, request), value ->
                new LeadDuplicateResponse().setDuplicate(value));
    }

    @Override
    public Result<Pager<NearbyLeadResponse>> nearby(
            AuthenticatedCaller caller, BigDecimal longitude, BigDecimal latitude,
            Integer radiusMeters, Integer page, Integer size) {
        return ResultUtil.map(queryService.nearby(caller, longitude, latitude,
                radiusMeters, page, size), source -> nearbyPage(caller, source));
    }

    @Override
    public Result<List<LeadResponse>> summaries(
            AuthenticatedCaller caller, List<Long> ids) {
        return ResultUtil.map(queryService.summaries(caller, ids), values ->
                responses(caller, values));
    }

    @Override
    public Result<List<String>> tags(AuthenticatedCaller caller) {
        return queryService.tags(caller);
    }

    private LeadResponse response(AuthenticatedCaller caller, LeadVO value) {
        return response(value, fileFieldResponseEnricher.crm(caller, "lead", value.id()));
    }

    private static LeadResponse response(
            LeadVO value, java.util.Map<String, java.util.List<io.github.archetom.crm.api.dto.response.AttachmentResponse>> fileFields) {
        return LeadResponseAssembler.toResponse(value).setFileFields(fileFields);
    }

    /** One FILE-value read for the whole projection instead of one per row. */
    private java.util.List<LeadResponse> responses(
            AuthenticatedCaller caller, java.util.List<LeadVO> values) {
        var fileFields = fileFieldResponseEnricher.crm(
                caller, "lead", values.stream().map(value -> value.id()).toList());
        return values.stream()
                .map(value -> response(value, fileFields.getOrDefault(value.id(), java.util.Map.of())))
                .toList();
    }

    private Pager<LeadResponse> page(AuthenticatedCaller caller, Pager<LeadVO> source) {
        Pager<LeadResponse> target = PageUtil.copy(source);
        target.setMeta(null);
        target.setObjectList(responses(caller, source.getObjectList()));
        return target;
    }

    private Pager<NearbyLeadResponse> nearbyPage(AuthenticatedCaller caller,
            Pager<io.github.archetom.crm.application.vo.NearbyLeadVO> source) {
        Pager<NearbyLeadResponse> target = PageUtil.copy(source);
        target.setMeta(null);
        java.util.List<LeadResponse> rows = responses(caller, source.getObjectList().stream()
                .map(io.github.archetom.crm.application.vo.NearbyLeadVO::lead).toList());
        java.util.List<NearbyLeadResponse> nearby = new java.util.ArrayList<>();
        for (int index = 0; index < source.getObjectList().size(); index++) {
            nearby.add(new NearbyLeadResponse()
                    .setLead(rows.get(index))
                    .setDistanceMeters(source.getObjectList().get(index).distanceMeters()));
        }
        target.setObjectList(java.util.List.copyOf(nearby));
        return target;
    }
}
