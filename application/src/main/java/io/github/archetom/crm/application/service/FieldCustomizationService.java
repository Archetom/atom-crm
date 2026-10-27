package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldLayoutUpdateRequest;
import io.github.archetom.crm.api.dto.request.FieldUpdateRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldPolicyReplaceRequest;
import io.github.archetom.crm.application.vo.BusinessObjectVO;
import io.github.archetom.crm.application.vo.FieldDefinitionVO;
import io.github.archetom.crm.application.vo.FieldLayoutVO;
import io.github.archetom.crm.application.vo.RoleFieldPolicyVO;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface FieldCustomizationService {
    Result<List<BusinessObjectVO>> listObjects(AuthenticatedCaller caller);
    Result<List<FieldDefinitionVO>> listFields(
            AuthenticatedCaller caller, String objectType, boolean includeInactive);
    Result<FieldDefinitionVO> createField(
            AuthenticatedCaller caller, String objectType, FieldCreateRequest request);
    Result<FieldDefinitionVO> updateField(
            AuthenticatedCaller caller, String objectType, Long fieldId, FieldUpdateRequest request);
    Result<Void> deactivateField(
            AuthenticatedCaller caller, String objectType, Long fieldId, Long version);
    Result<FieldLayoutVO> getLayout(AuthenticatedCaller caller, String objectType);
    Result<FieldLayoutVO> updateLayout(
            AuthenticatedCaller caller, String objectType, FieldLayoutUpdateRequest request);
    Result<RoleFieldPolicyVO> getRolePolicy(
            AuthenticatedCaller caller, Long roleId, String objectType);
    Result<RoleFieldPolicyVO> replaceRolePolicy(
            AuthenticatedCaller caller, Long roleId, String objectType,
            RoleFieldPolicyReplaceRequest request);
}
