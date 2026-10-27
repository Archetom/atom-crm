package io.github.archetom.crm.api.facade;

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
import io.github.archetom.common.result.Result;
import io.github.archetom.common.result.Pager;

import java.util.List;

/** Public facade for local organization membership and department operations. */
public interface OrganizationFacade {

    Result<OrganizationMembershipResponse> bootstrap(AuthenticatedCaller caller, OrganizationBootstrapRequest request);

    Result<MemberProfileResponse> updateMyProfile(AuthenticatedCaller caller, MemberProfileUpdateRequest request);

    Result<DepartmentResponse> createDepartment(AuthenticatedCaller caller, DepartmentCreateRequest request);

    Result<DepartmentResponse> renameDepartment(AuthenticatedCaller caller, Long departmentId, DepartmentRenameRequest request);

    Result<DepartmentResponse> moveDepartment(AuthenticatedCaller caller, Long departmentId, DepartmentMoveRequest request);

    Result<Void> deactivateDepartment(AuthenticatedCaller caller, Long departmentId);

    Result<Void> deleteDepartment(AuthenticatedCaller caller, Long departmentId);

    Result<List<DepartmentResponse>> listDepartments(AuthenticatedCaller caller);

    Result<List<DepartmentResponse>> findDepartmentsByIds(AuthenticatedCaller caller, List<Long> departmentIds);

    Result<List<DepartmentResponse>> listDepartmentDescendants(AuthenticatedCaller caller, Long departmentId);

    Result<Pager<DepartmentMemberProfileResponse>> listDepartmentMembers(
            AuthenticatedCaller caller, Long departmentId, DepartmentMemberQueryRequest request);

    Result<Pager<MemberProfileResponse>> queryMembers(AuthenticatedCaller caller, MemberProfileQueryRequest request);

    Result<MembershipRequestResponse> submitMembershipRequest(
            AuthenticatedCaller caller, MembershipRequestCreateRequest request);

    Result<Void> withdrawMembershipRequest(AuthenticatedCaller caller, Long requestId);

    Result<Pager<MembershipRequestResponse>> listMembershipRequests(
            AuthenticatedCaller caller, MembershipRequestQueryRequest request);

    Result<Pager<MembershipRequestResponse>> listMyMembershipRequests(
            AuthenticatedCaller caller, MembershipRequestQueryRequest request);

    Result<Void> approveMembershipRequest(AuthenticatedCaller caller, Long requestId);

    Result<Void> rejectMembershipRequest(AuthenticatedCaller caller, Long requestId);

    Result<List<TenantAccessResponse>> listAccessibleTenants(AuthenticatedCaller caller);

    Result<TenantAccessResponse> validateTenantAccess(AuthenticatedCaller caller, Long targetTenantId);
}
