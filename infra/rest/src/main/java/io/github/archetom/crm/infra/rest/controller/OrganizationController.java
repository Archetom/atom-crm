package io.github.archetom.crm.infra.rest.controller;

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
import io.github.archetom.crm.api.dto.response.DepartmentMemberProfilePageResponse;
import io.github.archetom.crm.api.dto.response.MemberProfilePageResponse;
import io.github.archetom.crm.api.dto.response.MemberProfileResponse;
import io.github.archetom.crm.api.dto.response.MembershipRequestPageResponse;
import io.github.archetom.crm.api.dto.response.MembershipRequestResponse;
import io.github.archetom.crm.api.dto.response.OrganizationMembershipResponse;
import io.github.archetom.crm.api.dto.response.TenantAccessResponse;
import io.github.archetom.crm.api.facade.OrganizationFacade;
import io.github.archetom.crm.infra.rest.result.RestErrorResult;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** HTTP adapter for local organization, department, and membership access use cases. */
@RestController
@RequestMapping("/api/v1/organization")
@RequiredArgsConstructor
@Tag(name = "Organization", description = "Local department and membership management")
public class OrganizationController {

    private final OrganizationFacade organizationFacade;
    private final AuthenticatedCallerMapper callerMapper;

    @Operation(summary = "Initialize the first member of the caller's tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant initialized",
                    content = @Content(schema = @Schema(implementation = OrganizationMembershipResponse.class))),
            @ApiResponse(responseCode = "409", description = "Tenant already initialized",
                    content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    @PostMapping("/bootstrap")
    public ResponseEntity<?> bootstrap(Authentication authentication,
                                       @RequestBody @Valid OrganizationBootstrapRequest request) {
        return response(organizationFacade.bootstrap(caller(authentication), request));
    }

    @Operation(summary = "Create or update the caller's local profile")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = MemberProfileResponse.class)))
    @PutMapping("/profile")
    public ResponseEntity<?> updateMyProfile(Authentication authentication,
                                             @RequestBody @Valid MemberProfileUpdateRequest request) {
        return response(organizationFacade.updateMyProfile(caller(authentication), request));
    }

    @Operation(summary = "Create a department in the caller's active tenant")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DepartmentResponse.class)))
    @PostMapping("/departments")
    public ResponseEntity<?> createDepartment(Authentication authentication,
                                              @RequestBody @Valid DepartmentCreateRequest request) {
        return response(organizationFacade.createDepartment(caller(authentication), request));
    }

    @Operation(summary = "List visible departments in the caller's active tenant")
    @ApiResponse(responseCode = "200", content = @Content(
            array = @ArraySchema(schema = @Schema(implementation = DepartmentResponse.class))))
    @GetMapping("/departments")
    public ResponseEntity<?> listDepartments(Authentication authentication) {
        return response(organizationFacade.listDepartments(caller(authentication)));
    }

    @Operation(summary = "Get visible departments by up to 200 IDs")
    @ApiResponse(responseCode = "200", content = @Content(
            array = @ArraySchema(schema = @Schema(implementation = DepartmentResponse.class))))
    @GetMapping("/departments/by-ids")
    public ResponseEntity<?> findDepartmentsByIds(Authentication authentication, @RequestParam List<Long> ids) {
        return response(organizationFacade.findDepartmentsByIds(caller(authentication), ids));
    }

    @Operation(summary = "List visible descendants of one department")
    @ApiResponse(responseCode = "200", content = @Content(
            array = @ArraySchema(schema = @Schema(implementation = DepartmentResponse.class))))
    @GetMapping("/departments/{departmentId}/descendants")
    public ResponseEntity<?> listDepartmentDescendants(Authentication authentication, @PathVariable Long departmentId) {
        return response(organizationFacade.listDepartmentDescendants(caller(authentication), departmentId));
    }

    @Operation(summary = "Rename a department")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DepartmentResponse.class)))
    @PutMapping("/departments/{departmentId}/name")
    public ResponseEntity<?> renameDepartment(Authentication authentication, @PathVariable Long departmentId,
                                              @RequestBody @Valid DepartmentRenameRequest request) {
        return response(organizationFacade.renameDepartment(caller(authentication), departmentId, request));
    }

    @Operation(summary = "Move a department beneath an in-tenant parent or the tenant root")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DepartmentResponse.class)))
    @PutMapping("/departments/{departmentId}/parent")
    public ResponseEntity<?> moveDepartment(Authentication authentication, @PathVariable Long departmentId,
                                            @RequestBody @Valid DepartmentMoveRequest request) {
        return response(organizationFacade.moveDepartment(caller(authentication), departmentId, request));
    }

    @Operation(summary = "Deactivate a department without active children or members")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Void.class)))
    @PostMapping("/departments/{departmentId}/deactivate")
    public ResponseEntity<?> deactivateDepartment(Authentication authentication, @PathVariable Long departmentId) {
        return response(organizationFacade.deactivateDepartment(caller(authentication), departmentId));
    }

    @Operation(summary = "Delete a department without active children or members")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Void.class)))
    @DeleteMapping("/departments/{departmentId}")
    public ResponseEntity<?> deleteDepartment(Authentication authentication, @PathVariable Long departmentId) {
        return response(organizationFacade.deleteDepartment(caller(authentication), departmentId));
    }

    @Operation(summary = "Page active local profiles in one visible department or its active descendant departments")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DepartmentMemberProfilePageResponse.class)))
    @GetMapping("/departments/{departmentId}/members")
    public ResponseEntity<?> listDepartmentMembers(Authentication authentication, @PathVariable Long departmentId,
                                                   @Valid DepartmentMemberQueryRequest request) {
        return response(organizationFacade.listDepartmentMembers(caller(authentication), departmentId, request));
    }

    @Operation(summary = "Page active member profiles visible in the caller's tenant")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = MemberProfilePageResponse.class)))
    @GetMapping("/members")
    public ResponseEntity<?> queryMembers(Authentication authentication, @Valid MemberProfileQueryRequest request) {
        return response(organizationFacade.queryMembers(caller(authentication), request));
    }

    @Operation(summary = "Submit a request to join an initialized target tenant")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = MembershipRequestResponse.class)))
    @PostMapping("/membership-requests")
    public ResponseEntity<?> submitMembershipRequest(Authentication authentication,
                                                     @RequestBody @Valid MembershipRequestCreateRequest request) {
        return response(organizationFacade.submitMembershipRequest(caller(authentication), request));
    }

    @Operation(summary = "Withdraw the caller's own membership request")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Void.class)))
    @PostMapping("/membership-requests/{requestId}/withdraw")
    public ResponseEntity<?> withdrawMembershipRequest(Authentication authentication, @PathVariable Long requestId) {
        return response(organizationFacade.withdrawMembershipRequest(caller(authentication), requestId));
    }

    @Operation(summary = "Page membership requests submitted by the caller")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = MembershipRequestPageResponse.class)))
    @GetMapping("/membership-requests/mine")
    public ResponseEntity<?> listMyMembershipRequests(Authentication authentication,
                                                      @Valid MembershipRequestQueryRequest request) {
        return response(organizationFacade.listMyMembershipRequests(caller(authentication), request));
    }

    @Operation(summary = "Page membership requests for the caller's active tenant")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = MembershipRequestPageResponse.class)))
    @GetMapping("/membership-requests")
    public ResponseEntity<?> listMembershipRequests(Authentication authentication,
                                                    @Valid MembershipRequestQueryRequest request) {
        return response(organizationFacade.listMembershipRequests(caller(authentication), request));
    }

    @Operation(summary = "Approve a membership request for the caller's active tenant")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Void.class)))
    @PostMapping("/membership-requests/{requestId}/approve")
    public ResponseEntity<?> approveMembershipRequest(Authentication authentication, @PathVariable Long requestId) {
        return response(organizationFacade.approveMembershipRequest(caller(authentication), requestId));
    }

    @Operation(summary = "Reject a membership request for the caller's active tenant")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Void.class)))
    @PostMapping("/membership-requests/{requestId}/reject")
    public ResponseEntity<?> rejectMembershipRequest(Authentication authentication, @PathVariable Long requestId) {
        return response(organizationFacade.rejectMembershipRequest(caller(authentication), requestId));
    }

    @Operation(summary = "List tenants to which the verified caller currently has local membership")
    @ApiResponse(responseCode = "200", content = @Content(
            array = @ArraySchema(schema = @Schema(implementation = TenantAccessResponse.class))))
    @GetMapping("/tenants")
    public ResponseEntity<?> listAccessibleTenants(Authentication authentication) {
        return response(organizationFacade.listAccessibleTenants(caller(authentication)));
    }

    @Operation(summary = "Validate local membership for a target tenant without switching credentials")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = TenantAccessResponse.class)))
    @GetMapping("/tenants/{targetTenantId}/access")
    public ResponseEntity<?> validateTenantAccess(Authentication authentication, @PathVariable Long targetTenantId) {
        return response(organizationFacade.validateTenantAccess(caller(authentication), targetTenantId));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
