package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.UserCreateRequest;
import io.github.archetom.crm.api.dto.request.UserQueryRequest;
import io.github.archetom.crm.api.dto.response.UserPageResponse;
import io.github.archetom.crm.api.dto.response.UserResponse;
import io.github.archetom.crm.api.facade.UserFacade;
import io.github.archetom.crm.infra.rest.result.RestErrorResult;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the bundled tenant-scoped User example. */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Tenant-scoped user lifecycle example")
public class UserController {

    private final UserFacade userFacade;
    private final AuthenticatedCallerMapper callerMapper;

    @Operation(summary = "Create a user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User created",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", description = "Username or email already exists",
                    content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    @PostMapping
    public ResponseEntity<?> createUser(Authentication authentication,
                                        @RequestBody @Validated UserCreateRequest request) {
        AuthenticatedCaller caller = callerMapper.from(authentication);
        log.info("Create user operation: tenantId={}", caller.tenantId());
        Result<UserResponse> result = userFacade.createUser(caller, request);
        return ResponseEntityUtil.assembleResponse(result);
    }

    @Operation(summary = "Get a visible user by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found in the caller's tenant",
                    content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserById(
            Authentication authentication,
            @Parameter(description = "Positive tenant-scoped user ID", example = "42")
            @PathVariable Long userId) {
        AuthenticatedCaller caller = callerMapper.from(authentication);
        Result<UserResponse> result = userFacade.getUserById(caller, userId);
        return ResponseEntityUtil.assembleResponse(result);
    }

    @Operation(summary = "Query visible users")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page containing UserResponse objects",
                    content = @Content(schema = @Schema(implementation = UserPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Query validation failed",
                    content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    @GetMapping
    public ResponseEntity<?> queryUsers(Authentication authentication, @Valid UserQueryRequest request) {
        AuthenticatedCaller caller = callerMapper.from(authentication);
        Result<UserPageResponse> result = userFacade.queryUsers(caller, request);
        return ResponseEntityUtil.assembleResponse(result);
    }

    @Operation(summary = "Change a user's non-deleted status",
            description = "DELETED is rejected; use the DELETE operation instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status changed"),
            @ApiResponse(responseCode = "400", description = "Unknown status",
                    content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", description = "Status transition rejected",
                    content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    @PutMapping("/{userId}/status")
    public ResponseEntity<?> updateUserStatus(
        Authentication authentication,
        @Parameter(description = "Positive tenant-scoped user ID", example = "42")
        @PathVariable Long userId,
        @Parameter(description = "Target status", example = "INACTIVE",
                schema = @Schema(allowableValues = {"ACTIVE", "INACTIVE", "LOCKED"}))
        @RequestParam String status
    ) {
        AuthenticatedCaller caller = callerMapper.from(authentication);
        Result<Void> result = userFacade.updateUserStatus(caller, userId, status);
        return ResponseEntityUtil.assembleResponse(result);
    }

    @Operation(summary = "Soft-delete a user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User deleted"),
            @ApiResponse(responseCode = "403", description = "Delete authority is missing",
                    content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", description = "Domain deletion rule rejected the operation",
                    content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    @DeleteMapping("/{userId}")
    public ResponseEntity<?> deleteUser(
            Authentication authentication,
            @Parameter(description = "Positive tenant-scoped user ID", example = "42")
            @PathVariable Long userId) {
        AuthenticatedCaller caller = callerMapper.from(authentication);
        Result<Void> result = userFacade.deleteUser(caller, userId);
        return ResponseEntityUtil.assembleResponse(result);
    }
}
