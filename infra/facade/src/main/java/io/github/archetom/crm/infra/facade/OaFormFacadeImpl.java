package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OaFormCreateRequest;
import io.github.archetom.crm.api.dto.request.OaFormQueryRequest;
import io.github.archetom.crm.api.dto.request.OaFormStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OaFormUpdateRequest;
import io.github.archetom.crm.api.dto.response.FieldOptionResponse;
import io.github.archetom.crm.api.dto.response.OaFormFieldResponse;
import io.github.archetom.crm.api.dto.response.OaFormResponse;
import io.github.archetom.crm.api.dto.response.OaFormApprovalDefinitionCandidateResponse;
import io.github.archetom.crm.api.dto.response.OaFormApprovalSelectionResponse;
import io.github.archetom.crm.api.dto.response.OaFormConfigurationFieldResponse;
import io.github.archetom.crm.api.dto.response.OaFormConfigurationOptionsResponse;
import io.github.archetom.crm.api.dto.response.OaFormFieldCandidateResponse;
import io.github.archetom.crm.api.dto.response.ApprovalSelectionCandidateResponse;
import io.github.archetom.crm.api.facade.OaFormFacade;
import io.github.archetom.crm.application.service.OaFormService;
import io.github.archetom.crm.application.vo.OaFormFieldVO;
import io.github.archetom.crm.application.vo.OaFormVO;
import io.github.archetom.crm.application.vo.OaFormConfigurationOptionsVO;
import io.github.archetom.crm.application.vo.OaFormFieldCandidateVO;
import io.github.archetom.crm.application.vo.ApprovalSelectionCandidateVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OaFormFacadeImpl implements OaFormFacade {

    private final OaFormService service;

    @Override
    public Result<OaFormConfigurationOptionsResponse> configurationOptions(
            AuthenticatedCaller caller) {
        return ResultUtil.map(service.configurationOptions(caller), this::configurationOptions);
    }

    @Override
    public Result<Pager<OaFormResponse>> query(
            AuthenticatedCaller caller, OaFormQueryRequest request) {
        return ResultUtil.map(service.query(caller, request), source -> {
            Pager<OaFormResponse> target = PageUtil.copy(source);
            target.setMeta(null);
            target.setObjectList(source.getObjectList().stream()
                    .map(this::response).toList());
            return target;
        });
    }

    @Override
    public Result<OaFormResponse> get(AuthenticatedCaller caller, Long formId) {
        return ResultUtil.map(service.get(caller, formId), this::response);
    }

    @Override
    public Result<List<OaFormResponse>> available(AuthenticatedCaller caller) {
        return ResultUtil.map(service.available(caller), values -> values.stream()
                .map(this::response).toList());
    }

    @Override
    public Result<OaFormResponse> getAvailable(
            AuthenticatedCaller caller, Long formId) {
        return ResultUtil.map(service.getAvailable(caller, formId), this::response);
    }

    @Override
    public Result<Pager<OaFormFieldCandidateResponse>> fieldCandidates(
            AuthenticatedCaller caller, Long formId, String fieldCode,
            String keyword, Integer page, Integer size) {
        return ResultUtil.map(service.fieldCandidates(caller, formId, fieldCode,
                keyword, page, size), this::fieldCandidatePager);
    }

    @Override
    public Result<Pager<ApprovalSelectionCandidateResponse>> approvalSelectionCandidates(
            AuthenticatedCaller caller, Long formId, String selectionCode,
            String keyword, Integer page, Integer size) {
        return ResultUtil.map(service.approvalSelectionCandidates(caller, formId,
                selectionCode, keyword, page, size), this::selectionCandidatePager);
    }

    @Override
    public Result<OaFormResponse> create(
            AuthenticatedCaller caller, OaFormCreateRequest request) {
        return ResultUtil.map(service.create(caller, request), this::response);
    }

    @Override
    public Result<OaFormResponse> update(
            AuthenticatedCaller caller, Long formId, OaFormUpdateRequest request) {
        return ResultUtil.map(service.update(caller, formId, request), this::response);
    }

    @Override
    public Result<OaFormResponse> updateStatus(
            AuthenticatedCaller caller, Long formId,
            OaFormStatusUpdateRequest request) {
        return ResultUtil.map(service.updateStatus(caller, formId, request), this::response);
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long formId, Long version) {
        return service.delete(caller, formId, version);
    }

    private OaFormResponse response(OaFormVO value) {
        return new OaFormResponse()
                .setId(value.id())
                .setCode(value.code())
                .setName(value.name())
                .setDescription(value.description())
                .setApprovalDefinitionId(value.approvalDefinitionId())
                .setApprovalDefinitionName(value.approvalDefinitionName())
                .setApprovalDefinitionStatus(value.approvalDefinitionStatus())
                .setCreatedByActorId(value.createdByActorId())
                .setStatus(value.status())
                .setApprovalSelections(value.approvalSelections().stream().map(selection ->
                        new OaFormApprovalSelectionResponse(selection.code(), selection.name(),
                                selection.mode())).toList())
                .setFields(value.fields().stream().map(this::field).toList())
                .setVersion(value.version())
                .setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }

    private OaFormConfigurationOptionsResponse configurationOptions(
            OaFormConfigurationOptionsVO value) {
        return new OaFormConfigurationOptionsResponse(value.approvalDefinitions().stream()
                .map(approval -> new OaFormApprovalDefinitionCandidateResponse(
                        approval.id(), approval.name(), approval.requiredFieldCodes()))
                .toList(), value.fields().stream().map(field -> new OaFormConfigurationFieldResponse(
                        field.fieldCode(), field.displayName(), field.fieldType(), field.required()))
                .toList());
    }

    private Pager<OaFormFieldCandidateResponse> fieldCandidatePager(
            Pager<OaFormFieldCandidateVO> source) {
        Pager<OaFormFieldCandidateResponse> result = PageUtil.copy(source);
        result.setMeta(null);
        result.setObjectList(source.getObjectList().stream().map(value ->
                new OaFormFieldCandidateResponse(value.id(), value.displayName(),
                        value.valueType())).toList());
        return result;
    }

    private Pager<ApprovalSelectionCandidateResponse> selectionCandidatePager(
            Pager<ApprovalSelectionCandidateVO> source) {
        Pager<ApprovalSelectionCandidateResponse> result = PageUtil.copy(source);
        result.setMeta(null);
        result.setObjectList(source.getObjectList().stream().map(value ->
                new ApprovalSelectionCandidateResponse(value.actorId(),
                        value.displayName())).toList());
        return result;
    }

    private OaFormFieldResponse field(OaFormFieldVO value) {
        return new OaFormFieldResponse()
                .setFieldCode(value.fieldCode())
                .setDisplayName(value.displayName())
                .setDescription(value.description())
                .setFieldType(value.fieldType())
                .setBuiltin(value.builtin())
                .setRequired(value.required())
                .setSearchable(value.searchable())
                .setStatus(value.status())
                .setSortOrder(value.sortOrder())
                .setOptions(value.options().stream().map(option ->
                        new FieldOptionResponse().setValue(option.value())
                                .setLabel(option.label()).setOrder(option.order())).toList())
                .setAccessLevel(value.accessLevel())
                .setMaskType(value.maskType());
    }
}
