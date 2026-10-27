package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.DepartmentCreateRequest;
import io.github.archetom.crm.api.dto.request.DepartmentMemberQueryRequest;
import io.github.archetom.crm.api.dto.request.DepartmentMoveRequest;
import io.github.archetom.crm.api.dto.request.DepartmentRenameRequest;
import io.github.archetom.crm.api.dto.request.MemberProfileUpdateRequest;
import io.github.archetom.crm.api.dto.request.MemberProfileQueryRequest;
import io.github.archetom.crm.api.dto.request.MembershipRequestCreateRequest;
import io.github.archetom.crm.api.dto.request.MembershipRequestQueryRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.response.DepartmentResponse;
import io.github.archetom.crm.api.dto.response.DepartmentMemberProfileResponse;
import io.github.archetom.crm.api.dto.response.MemberProfileResponse;
import io.github.archetom.crm.api.dto.response.MembershipRequestResponse;
import io.github.archetom.crm.api.dto.response.OrganizationMembershipResponse;
import io.github.archetom.crm.api.dto.response.TenantAccessResponse;
import io.github.archetom.crm.api.facade.OrganizationFacade;
import io.github.archetom.crm.application.service.OrganizationService;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Result;
import io.github.archetom.common.result.Pager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Facade adapter that preserves application error results while mapping successful data. */
@Service
@RequiredArgsConstructor
public class OrganizationFacadeImpl implements OrganizationFacade {

    private final OrganizationService organizationService;

    @Override
    public Result<OrganizationMembershipResponse> bootstrap(AuthenticatedCaller caller, OrganizationBootstrapRequest request) {
        return ResultUtil.map(organizationService.bootstrap(caller, request), OrganizationResponseAssembler::toResponse);
    }

    @Override
    public Result<MemberProfileResponse> updateMyProfile(AuthenticatedCaller caller, MemberProfileUpdateRequest request) {
        return ResultUtil.map(organizationService.updateMyProfile(caller, request), OrganizationResponseAssembler::toResponse);
    }

    @Override
    public Result<DepartmentResponse> createDepartment(AuthenticatedCaller caller, DepartmentCreateRequest request) {
        return ResultUtil.map(organizationService.createDepartment(caller, request), OrganizationResponseAssembler::toResponse);
    }

    @Override
    public Result<DepartmentResponse> renameDepartment(
            AuthenticatedCaller caller, Long departmentId, DepartmentRenameRequest request) {
        return ResultUtil.map(organizationService.renameDepartment(caller, departmentId, request),
                OrganizationResponseAssembler::toResponse);
    }

    @Override
    public Result<DepartmentResponse> moveDepartment(
            AuthenticatedCaller caller, Long departmentId, DepartmentMoveRequest request) {
        return ResultUtil.map(organizationService.moveDepartment(caller, departmentId, request),
                OrganizationResponseAssembler::toResponse);
    }

    @Override
    public Result<Void> deactivateDepartment(AuthenticatedCaller caller, Long departmentId) {
        return organizationService.deactivateDepartment(caller, departmentId);
    }

    @Override
    public Result<Void> deleteDepartment(AuthenticatedCaller caller, Long departmentId) {
        return organizationService.deleteDepartment(caller, departmentId);
    }

    @Override
    public Result<List<DepartmentResponse>> listDepartments(AuthenticatedCaller caller) {
        return ResultUtil.map(organizationService.listDepartments(caller), OrganizationResponseAssembler::toDepartmentResponses);
    }

    @Override
    public Result<List<DepartmentResponse>> findDepartmentsByIds(
            AuthenticatedCaller caller, List<Long> departmentIds) {
        return ResultUtil.map(organizationService.findDepartmentsByIds(caller, departmentIds),
                OrganizationResponseAssembler::toDepartmentResponses);
    }

    @Override
    public Result<List<DepartmentResponse>> listDepartmentDescendants(AuthenticatedCaller caller, Long departmentId) {
        return ResultUtil.map(organizationService.listDepartmentDescendants(caller, departmentId),
                OrganizationResponseAssembler::toDepartmentResponses);
    }

    @Override
    public Result<Pager<DepartmentMemberProfileResponse>> listDepartmentMembers(
            AuthenticatedCaller caller, Long departmentId, DepartmentMemberQueryRequest request) {
        return ResultUtil.map(organizationService.listDepartmentMembers(caller, departmentId, request),
                OrganizationResponseAssembler::toDepartmentMemberProfilePager);
    }

    @Override
    public Result<Pager<MemberProfileResponse>> queryMembers(AuthenticatedCaller caller, MemberProfileQueryRequest request) {
        return ResultUtil.map(organizationService.queryMembers(caller, request),
                OrganizationResponseAssembler::toMemberProfilePager);
    }

    @Override
    public Result<MembershipRequestResponse> submitMembershipRequest(
            AuthenticatedCaller caller, MembershipRequestCreateRequest request) {
        return ResultUtil.map(organizationService.submitMembershipRequest(caller, request),
                OrganizationResponseAssembler::toResponse);
    }

    @Override
    public Result<Void> withdrawMembershipRequest(AuthenticatedCaller caller, Long requestId) {
        return organizationService.withdrawMembershipRequest(caller, requestId);
    }

    @Override
    public Result<Pager<MembershipRequestResponse>> listMembershipRequests(
            AuthenticatedCaller caller, MembershipRequestQueryRequest request) {
        return ResultUtil.map(organizationService.listMembershipRequests(caller, request),
                OrganizationResponseAssembler::toMembershipRequestPager);
    }

    @Override
    public Result<Pager<MembershipRequestResponse>> listMyMembershipRequests(
            AuthenticatedCaller caller, MembershipRequestQueryRequest request) {
        return ResultUtil.map(organizationService.listMyMembershipRequests(caller, request),
                OrganizationResponseAssembler::toMembershipRequestPager);
    }

    @Override
    public Result<Void> approveMembershipRequest(AuthenticatedCaller caller, Long requestId) {
        return organizationService.approveMembershipRequest(caller, requestId);
    }

    @Override
    public Result<Void> rejectMembershipRequest(AuthenticatedCaller caller, Long requestId) {
        return organizationService.rejectMembershipRequest(caller, requestId);
    }

    @Override
    public Result<List<TenantAccessResponse>> listAccessibleTenants(AuthenticatedCaller caller) {
        return ResultUtil.map(organizationService.listAccessibleTenants(caller),
                OrganizationResponseAssembler::toTenantAccessResponses);
    }

    @Override
    public Result<TenantAccessResponse> validateTenantAccess(AuthenticatedCaller caller, Long targetTenantId) {
        return ResultUtil.map(organizationService.validateTenantAccess(caller, targetTenantId),
                OrganizationResponseAssembler::toResponse);
    }
}
