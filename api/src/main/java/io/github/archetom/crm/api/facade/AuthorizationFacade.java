package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.MemberRoleBatchQueryRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.api.dto.request.RoleAssignmentCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleCopyRequest;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantReplaceRequest;
import io.github.archetom.crm.api.dto.request.RoleQueryRequest;
import io.github.archetom.crm.api.dto.request.RoleUpdateRequest;
import io.github.archetom.crm.api.dto.response.AuthorityCapabilityResponse;
import io.github.archetom.crm.api.dto.response.MemberAuthorizationResponse;
import io.github.archetom.crm.api.dto.response.RoleAssignmentResponse;
import io.github.archetom.crm.api.dto.response.TenantRoleResponse;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;
import java.util.Map;

/** Public tenant authorization facade. */
public interface AuthorizationFacade {

    Result<MemberAuthorizationResponse> getMyAuthorization(AuthenticatedCaller caller);

    Result<List<AuthorityCapabilityResponse>> listCapabilities(AuthenticatedCaller caller);

    Result<Pager<TenantRoleResponse>> queryRoles(AuthenticatedCaller caller, RoleQueryRequest request);

    Result<TenantRoleResponse> createRole(AuthenticatedCaller caller, RoleCreateRequest request);

    Result<TenantRoleResponse> updateRole(AuthenticatedCaller caller, Long roleId, RoleUpdateRequest request);

    Result<Void> deleteRole(AuthenticatedCaller caller, Long roleId, Long version);

    Result<TenantRoleResponse> copyRole(AuthenticatedCaller caller, Long roleId, RoleCopyRequest request);

    Result<TenantRoleResponse> replaceRoleGrants(
            AuthenticatedCaller caller, Long roleId, RoleGrantReplaceRequest request);

    Result<Pager<RoleAssignmentResponse>> listRoleAssignments(
            AuthenticatedCaller caller, Long roleId, QueryRequest request);

    Result<Void> assignRole(AuthenticatedCaller caller, Long roleId, RoleAssignmentCreateRequest request);

    Result<Void> removeRoleAssignment(AuthenticatedCaller caller, Long roleId, Long actorId);

    Result<MemberAuthorizationResponse> getMemberRoles(AuthenticatedCaller caller, Long actorId);

    Result<Map<Long, List<TenantRoleResponse>>> getMembersRoles(
            AuthenticatedCaller caller, MemberRoleBatchQueryRequest request);
}
