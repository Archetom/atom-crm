package io.github.archetom.crm.application.service;

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
import io.github.archetom.crm.application.vo.DepartmentVO;
import io.github.archetom.crm.application.vo.DepartmentMemberProfileVO;
import io.github.archetom.crm.application.vo.MemberProfileVO;
import io.github.archetom.crm.application.vo.MembershipRequestVO;
import io.github.archetom.crm.application.vo.OrganizationMembershipVO;
import io.github.archetom.crm.application.vo.TenantAccessVO;
import io.github.archetom.common.result.Result;
import io.github.archetom.common.result.Pager;

import java.util.List;

/** Organization, department, and local membership use-case boundary. */
public interface OrganizationService {

    Result<OrganizationMembershipVO> bootstrap(AuthenticatedCaller caller, OrganizationBootstrapRequest request);

    Result<MemberProfileVO> updateMyProfile(AuthenticatedCaller caller, MemberProfileUpdateRequest request);

    Result<DepartmentVO> createDepartment(AuthenticatedCaller caller, DepartmentCreateRequest request);

    Result<DepartmentVO> renameDepartment(AuthenticatedCaller caller, Long departmentId, DepartmentRenameRequest request);

    Result<DepartmentVO> moveDepartment(AuthenticatedCaller caller, Long departmentId, DepartmentMoveRequest request);

    Result<Void> deactivateDepartment(AuthenticatedCaller caller, Long departmentId);

    Result<Void> deleteDepartment(AuthenticatedCaller caller, Long departmentId);

    Result<List<DepartmentVO>> listDepartments(AuthenticatedCaller caller);

    Result<List<DepartmentVO>> findDepartmentsByIds(AuthenticatedCaller caller, List<Long> departmentIds);

    Result<List<DepartmentVO>> listDepartmentDescendants(AuthenticatedCaller caller, Long departmentId);

    Result<Pager<DepartmentMemberProfileVO>> listDepartmentMembers(
            AuthenticatedCaller caller, Long departmentId, DepartmentMemberQueryRequest request);

    Result<Pager<MemberProfileVO>> queryMembers(AuthenticatedCaller caller, MemberProfileQueryRequest request);

    Result<MembershipRequestVO> submitMembershipRequest(
            AuthenticatedCaller caller, MembershipRequestCreateRequest request);

    Result<Void> withdrawMembershipRequest(AuthenticatedCaller caller, Long requestId);

    Result<Pager<MembershipRequestVO>> listMembershipRequests(
            AuthenticatedCaller caller, MembershipRequestQueryRequest request);

    Result<Pager<MembershipRequestVO>> listMyMembershipRequests(
            AuthenticatedCaller caller, MembershipRequestQueryRequest request);

    Result<Void> approveMembershipRequest(AuthenticatedCaller caller, Long requestId);

    Result<Void> rejectMembershipRequest(AuthenticatedCaller caller, Long requestId);

    Result<List<TenantAccessVO>> listAccessibleTenants(AuthenticatedCaller caller);

    Result<TenantAccessVO> validateTenantAccess(AuthenticatedCaller caller, Long targetTenantId);
}
