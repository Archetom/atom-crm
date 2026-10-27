package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldLayoutUpdateRequest;
import io.github.archetom.crm.api.dto.request.FieldUpdateRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldPolicyReplaceRequest;
import io.github.archetom.crm.api.dto.response.BusinessObjectResponse;
import io.github.archetom.crm.api.dto.response.FieldDefinitionResponse;
import io.github.archetom.crm.api.dto.response.FieldLayoutResponse;
import io.github.archetom.crm.api.dto.response.RoleFieldPolicyResponse;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface FieldCustomizationFacade {
    Result<List<BusinessObjectResponse>> listObjects(AuthenticatedCaller caller);
    Result<List<FieldDefinitionResponse>> listFields(
            AuthenticatedCaller caller, String objectType, boolean includeInactive);
    Result<FieldDefinitionResponse> createField(
            AuthenticatedCaller caller, String objectType, FieldCreateRequest request);
    Result<FieldDefinitionResponse> updateField(
            AuthenticatedCaller caller, String objectType, Long fieldId, FieldUpdateRequest request);
    Result<Void> deactivateField(
            AuthenticatedCaller caller, String objectType, Long fieldId, Long version);
    Result<FieldLayoutResponse> getLayout(AuthenticatedCaller caller, String objectType);
    Result<FieldLayoutResponse> updateLayout(
            AuthenticatedCaller caller, String objectType, FieldLayoutUpdateRequest request);
    Result<RoleFieldPolicyResponse> getRolePolicy(
            AuthenticatedCaller caller, Long roleId, String objectType);
    Result<RoleFieldPolicyResponse> replaceRolePolicy(
            AuthenticatedCaller caller, Long roleId, String objectType,
            RoleFieldPolicyReplaceRequest request);
}
