package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldLayoutUpdateRequest;
import io.github.archetom.crm.api.dto.request.FieldUpdateRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldPolicyReplaceRequest;
import io.github.archetom.crm.api.dto.response.BusinessObjectResponse;
import io.github.archetom.crm.api.dto.response.FieldDefinitionResponse;
import io.github.archetom.crm.api.dto.response.FieldLayoutResponse;
import io.github.archetom.crm.api.dto.response.FieldOptionResponse;
import io.github.archetom.crm.api.dto.response.FilterOperatorOptionResponse;
import io.github.archetom.crm.api.dto.response.RoleFieldAccessResponse;
import io.github.archetom.crm.api.dto.response.RoleFieldPolicyResponse;
import io.github.archetom.crm.api.facade.FieldCustomizationFacade;
import io.github.archetom.crm.application.service.FieldCustomizationService;
import io.github.archetom.crm.application.vo.BusinessObjectVO;
import io.github.archetom.crm.application.vo.FieldDefinitionVO;
import io.github.archetom.crm.application.vo.FieldLayoutVO;
import io.github.archetom.crm.application.vo.RoleFieldPolicyVO;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FieldCustomizationFacadeImpl implements FieldCustomizationFacade {

    private final FieldCustomizationService service;

    @Override
    public Result<List<BusinessObjectResponse>> listObjects(AuthenticatedCaller caller) {
        return ResultUtil.map(service.listObjects(caller), values -> values.stream()
                .map(this::toResponse).toList());
    }

    @Override
    public Result<List<FieldDefinitionResponse>> listFields(
            AuthenticatedCaller caller, String objectType, boolean includeInactive) {
        return ResultUtil.map(service.listFields(caller, objectType, includeInactive), values ->
                values.stream().map(this::toResponse).toList());
    }

    @Override
    public Result<FieldDefinitionResponse> createField(
            AuthenticatedCaller caller, String objectType, FieldCreateRequest request) {
        return ResultUtil.map(service.createField(caller, objectType, request), this::toResponse);
    }

    @Override
    public Result<FieldDefinitionResponse> updateField(
            AuthenticatedCaller caller, String objectType, Long fieldId, FieldUpdateRequest request) {
        return ResultUtil.map(service.updateField(caller, objectType, fieldId, request), this::toResponse);
    }

    @Override
    public Result<Void> deactivateField(
            AuthenticatedCaller caller, String objectType, Long fieldId, Long version) {
        return service.deactivateField(caller, objectType, fieldId, version);
    }

    @Override
    public Result<FieldLayoutResponse> getLayout(AuthenticatedCaller caller, String objectType) {
        return ResultUtil.map(service.getLayout(caller, objectType), this::toResponse);
    }

    @Override
    public Result<FieldLayoutResponse> updateLayout(
            AuthenticatedCaller caller, String objectType, FieldLayoutUpdateRequest request) {
        return ResultUtil.map(service.updateLayout(caller, objectType, request), this::toResponse);
    }

    @Override
    public Result<RoleFieldPolicyResponse> getRolePolicy(
            AuthenticatedCaller caller, Long roleId, String objectType) {
        return ResultUtil.map(service.getRolePolicy(caller, roleId, objectType), this::toResponse);
    }

    @Override
    public Result<RoleFieldPolicyResponse> replaceRolePolicy(
            AuthenticatedCaller caller, Long roleId, String objectType,
            RoleFieldPolicyReplaceRequest request) {
        return ResultUtil.map(service.replaceRolePolicy(caller, roleId, objectType, request), this::toResponse);
    }

    private BusinessObjectResponse toResponse(BusinessObjectVO value) {
        return new BusinessObjectResponse().setCode(value.code()).setDisplayName(value.displayName());
    }

    private FieldDefinitionResponse toResponse(FieldDefinitionVO value) {
        return new FieldDefinitionResponse().setId(value.id()).setObjectType(value.objectType())
                .setCode(value.code()).setDisplayName(value.displayName())
                .setDescription(value.description()).setFieldType(value.fieldType())
                .setBuiltin(value.builtin()).setRequired(value.required()).setUnique(value.unique())
                .setSearchable(value.searchable()).setStatus(value.status())
                .setDisplayOrder(value.displayOrder()).setSectionName(value.sectionName())
                .setOptions(value.options().stream().map(option -> new FieldOptionResponse()
                        .setValue(option.value()).setLabel(option.label()).setOrder(option.order())).toList())
                .setFilterOperators(value.filterOperators().stream()
                        .map(this::toFilterOperatorOption).toList())
                .setVersion(value.version()).setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }

    private FilterOperatorOptionResponse toFilterOperatorOption(
            io.github.archetom.crm.application.vo.FilterOperatorOptionVO operator) {
        return new FilterOperatorOptionResponse(operator.name(), operator.minimumValueCount(),
                operator.maximumValueCount());
    }

    private FieldLayoutResponse toResponse(FieldLayoutVO value) {
        return new FieldLayoutResponse().setObjectType(value.objectType())
                .setOrderedCodes(value.orderedCodes()).setHiddenCodes(value.hiddenCodes())
                .setPinnedCodes(value.pinnedCodes()).setColumnWidths(value.columnWidths())
                .setVersion(value.version());
    }

    private RoleFieldPolicyResponse toResponse(RoleFieldPolicyVO value) {
        return new RoleFieldPolicyResponse().setRoleId(value.roleId()).setObjectType(value.objectType())
                .setAccesses(value.accesses().stream().map(access -> new RoleFieldAccessResponse()
                        .setFieldId(access.fieldId()).setFieldCode(access.fieldCode())
                        .setDisplayName(access.displayName()).setAccessLevel(access.accessLevel())
                        .setMaskType(access.maskType())).toList()).setVersion(value.version());
    }
}
