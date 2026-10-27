package io.github.archetom.crm.infra.facade;

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
import io.github.archetom.crm.api.facade.AuthorizationFacade;
import io.github.archetom.crm.application.service.AuthorizationService;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Facade adapter for tenant authorization use cases. */
@Service
@RequiredArgsConstructor
public class AuthorizationFacadeImpl implements AuthorizationFacade {

    private final AuthorizationService service;

    @Override
    public Result<MemberAuthorizationResponse> getMyAuthorization(AuthenticatedCaller caller) {
        return ResultUtil.map(service.getMyAuthorization(caller), AuthorizationResponseAssembler::toResponse);
    }

    @Override
    public Result<List<AuthorityCapabilityResponse>> listCapabilities(AuthenticatedCaller caller) {
        return ResultUtil.map(service.listCapabilities(caller), AuthorizationResponseAssembler::toCapabilityResponses);
    }

    @Override
    public Result<Pager<TenantRoleResponse>> queryRoles(AuthenticatedCaller caller, RoleQueryRequest request) {
        return ResultUtil.map(service.queryRoles(caller, request), AuthorizationResponseAssembler::toRolePager);
    }

    @Override
    public Result<TenantRoleResponse> createRole(AuthenticatedCaller caller, RoleCreateRequest request) {
        return ResultUtil.map(service.createRole(caller, request), AuthorizationResponseAssembler::toResponse);
    }

    @Override
    public Result<TenantRoleResponse> updateRole(
            AuthenticatedCaller caller, Long roleId, RoleUpdateRequest request) {
        return ResultUtil.map(service.updateRole(caller, roleId, request), AuthorizationResponseAssembler::toResponse);
    }

    @Override
    public Result<Void> deleteRole(AuthenticatedCaller caller, Long roleId, Long version) {
        return service.deleteRole(caller, roleId, version);
    }

    @Override
    public Result<TenantRoleResponse> copyRole(
            AuthenticatedCaller caller, Long roleId, RoleCopyRequest request) {
        return ResultUtil.map(service.copyRole(caller, roleId, request), AuthorizationResponseAssembler::toResponse);
    }

    @Override
    public Result<TenantRoleResponse> replaceRoleGrants(
            AuthenticatedCaller caller, Long roleId, RoleGrantReplaceRequest request) {
        return ResultUtil.map(service.replaceRoleGrants(caller, roleId, request),
                AuthorizationResponseAssembler::toResponse);
    }

    @Override
    public Result<Pager<RoleAssignmentResponse>> listRoleAssignments(
            AuthenticatedCaller caller, Long roleId, QueryRequest request) {
        return ResultUtil.map(service.listRoleAssignments(caller, roleId, request),
                AuthorizationResponseAssembler::toAssignmentPager);
    }

    @Override
    public Result<Void> assignRole(
            AuthenticatedCaller caller, Long roleId, RoleAssignmentCreateRequest request) {
        return service.assignRole(caller, roleId, request);
    }

    @Override
    public Result<Void> removeRoleAssignment(AuthenticatedCaller caller, Long roleId, Long actorId) {
        return service.removeRoleAssignment(caller, roleId, actorId);
    }

    @Override
    public Result<MemberAuthorizationResponse> getMemberRoles(AuthenticatedCaller caller, Long actorId) {
        return ResultUtil.map(service.getMemberRoles(caller, actorId), AuthorizationResponseAssembler::toResponse);
    }

    @Override
    public Result<Map<Long, List<TenantRoleResponse>>> getMembersRoles(
            AuthenticatedCaller caller, MemberRoleBatchQueryRequest request) {
        return ResultUtil.map(service.getMembersRoles(caller, request),
                AuthorizationResponseAssembler::toMemberRoleResponses);
    }
}
