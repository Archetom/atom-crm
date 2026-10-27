package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.MemberRoleBatchQueryRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.api.dto.request.RoleAssignmentCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleCopyRequest;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantReplaceRequest;
import io.github.archetom.crm.api.dto.request.RoleQueryRequest;
import io.github.archetom.crm.api.dto.request.RoleUpdateRequest;
import io.github.archetom.crm.api.facade.AuthorizationFacade;
import io.github.archetom.crm.api.dto.response.AuthorityCapabilityResponse;
import io.github.archetom.crm.api.dto.response.MemberAuthorizationResponse;
import io.github.archetom.crm.api.dto.response.RoleAssignmentPageResponse;
import io.github.archetom.crm.api.dto.response.TenantRolePageResponse;
import io.github.archetom.crm.api.dto.response.TenantRoleResponse;
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

/** HTTP adapter for tenant roles, grants, and explicit member assignments. */
@RestController
@RequestMapping("/api/v1/authorization")
@RequiredArgsConstructor
@Tag(name = "Authorization", description = "Tenant roles and effective authorization")
public class AuthorizationController {

    private final AuthorizationFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping("/me")
    @Operation(summary = "Get the caller's effective tenant authorization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Effective authorities and scopes",
                    content = @Content(schema = @Schema(implementation = MemberAuthorizationResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller has no active tenant membership",
                    content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> getMyAuthorization(Authentication authentication) {
        return response(facade.getMyAuthorization(caller(authentication)));
    }

    @GetMapping("/capabilities")
    @Operation(summary = "List tenant-assignable capabilities")
    @ApiResponse(responseCode = "200", content = @Content(
            array = @ArraySchema(schema = @Schema(implementation = AuthorityCapabilityResponse.class))))
    public ResponseEntity<?> listCapabilities(Authentication authentication) {
        return response(facade.listCapabilities(caller(authentication)));
    }

    @GetMapping("/roles")
    @Operation(summary = "Query tenant roles")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = TenantRolePageResponse.class)))
    public ResponseEntity<?> queryRoles(Authentication authentication, @Valid RoleQueryRequest request) {
        return response(facade.queryRoles(caller(authentication), request));
    }

    @PostMapping("/roles")
    @Operation(summary = "Create a custom tenant role")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = TenantRoleResponse.class)))
    public ResponseEntity<?> createRole(Authentication authentication,
                                        @RequestBody @Valid RoleCreateRequest request) {
        return response(facade.createRole(caller(authentication), request));
    }

    @PutMapping("/roles/{roleId}")
    @Operation(summary = "Update a custom tenant role")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = TenantRoleResponse.class)))
    public ResponseEntity<?> updateRole(Authentication authentication, @PathVariable Long roleId,
                                        @RequestBody @Valid RoleUpdateRequest request) {
        return response(facade.updateRole(caller(authentication), roleId, request));
    }

    @DeleteMapping("/roles/{roleId}")
    @Operation(summary = "Delete an unassigned custom tenant role")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Void.class)))
    public ResponseEntity<?> deleteRole(Authentication authentication, @PathVariable Long roleId,
                                        @RequestParam Long version) {
        return response(facade.deleteRole(caller(authentication), roleId, version));
    }

    @PostMapping("/roles/{roleId}/copy")
    @Operation(summary = "Copy a tenant role without assignments")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = TenantRoleResponse.class)))
    public ResponseEntity<?> copyRole(Authentication authentication, @PathVariable Long roleId,
                                      @RequestBody @Valid RoleCopyRequest request) {
        return response(facade.copyRole(caller(authentication), roleId, request));
    }

    @PutMapping("/roles/{roleId}/grants")
    @Operation(summary = "Replace all grants on a custom role")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = TenantRoleResponse.class)))
    public ResponseEntity<?> replaceRoleGrants(Authentication authentication, @PathVariable Long roleId,
                                               @RequestBody @Valid RoleGrantReplaceRequest request) {
        return response(facade.replaceRoleGrants(caller(authentication), roleId, request));
    }

    @GetMapping("/roles/{roleId}/assignments")
    @Operation(summary = "Page members explicitly assigned to a role")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = RoleAssignmentPageResponse.class)))
    public ResponseEntity<?> listRoleAssignments(Authentication authentication, @PathVariable Long roleId,
                                                 @Valid QueryRequest request) {
        return response(facade.listRoleAssignments(caller(authentication), roleId, request));
    }

    @PostMapping("/roles/{roleId}/assignments")
    @Operation(summary = "Assign a role to active tenant members")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Void.class)))
    public ResponseEntity<?> assignRole(Authentication authentication, @PathVariable Long roleId,
                                        @RequestBody @Valid RoleAssignmentCreateRequest request) {
        return response(facade.assignRole(caller(authentication), roleId, request));
    }

    @DeleteMapping("/roles/{roleId}/assignments/{actorId}")
    @Operation(summary = "Remove one explicit role assignment")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Void.class)))
    public ResponseEntity<?> removeRoleAssignment(Authentication authentication, @PathVariable Long roleId,
                                                  @PathVariable Long actorId) {
        return response(facade.removeRoleAssignment(caller(authentication), roleId, actorId));
    }

    @GetMapping("/members/{actorId}/roles")
    @Operation(summary = "Get roles for one visible tenant member")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = MemberAuthorizationResponse.class)))
    public ResponseEntity<?> getMemberRoles(Authentication authentication, @PathVariable Long actorId) {
        return response(facade.getMemberRoles(caller(authentication), actorId));
    }

    @PostMapping("/members/roles/query")
    @Operation(summary = "Get roles for up to 200 visible tenant members")
    public ResponseEntity<?> getMembersRoles(Authentication authentication,
                                             @RequestBody @Valid MemberRoleBatchQueryRequest request) {
        return response(facade.getMembersRoles(caller(authentication), request));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
