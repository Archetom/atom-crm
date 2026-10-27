package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.MemberRoleBatchQueryRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.api.dto.request.RoleAssignmentCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleCopyRequest;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantReplaceRequest;
import io.github.archetom.crm.api.dto.request.RoleQueryRequest;
import io.github.archetom.crm.api.dto.request.RoleUpdateRequest;
import io.github.archetom.crm.application.vo.AuthorityCapabilityVO;
import io.github.archetom.crm.application.vo.MemberAuthorizationVO;
import io.github.archetom.crm.application.vo.RoleAssignmentVO;
import io.github.archetom.crm.application.vo.TenantRoleVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;
import java.util.Map;

/** Tenant role, grant, assignment, and effective-authorization use cases. */
public interface AuthorizationService {

    Result<MemberAuthorizationVO> getMyAuthorization(AuthenticatedCaller caller);

    Result<List<AuthorityCapabilityVO>> listCapabilities(AuthenticatedCaller caller);

    Result<Pager<TenantRoleVO>> queryRoles(AuthenticatedCaller caller, RoleQueryRequest request);

    Result<TenantRoleVO> createRole(AuthenticatedCaller caller, RoleCreateRequest request);

    Result<TenantRoleVO> updateRole(AuthenticatedCaller caller, Long roleId, RoleUpdateRequest request);

    Result<Void> deleteRole(AuthenticatedCaller caller, Long roleId, Long version);

    Result<TenantRoleVO> copyRole(AuthenticatedCaller caller, Long roleId, RoleCopyRequest request);

    Result<TenantRoleVO> replaceRoleGrants(
            AuthenticatedCaller caller, Long roleId, RoleGrantReplaceRequest request);

    Result<Pager<RoleAssignmentVO>> listRoleAssignments(
            AuthenticatedCaller caller, Long roleId, QueryRequest request);

    Result<Void> assignRole(
            AuthenticatedCaller caller, Long roleId, RoleAssignmentCreateRequest request);

    Result<Void> removeRoleAssignment(AuthenticatedCaller caller, Long roleId, Long actorId);

    Result<MemberAuthorizationVO> getMemberRoles(AuthenticatedCaller caller, Long actorId);

    Result<Map<Long, List<TenantRoleVO>>> getMembersRoles(
            AuthenticatedCaller caller, MemberRoleBatchQueryRequest request);
}
