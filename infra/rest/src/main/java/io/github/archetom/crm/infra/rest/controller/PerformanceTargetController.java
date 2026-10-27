package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.dto.request.PerformanceTargetCreateRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetImportRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetQueryRequest;
import io.github.archetom.crm.api.dto.request.PerformanceTargetUpdateRequest;
import io.github.archetom.crm.api.dto.response.PerformanceTargetImportResponse;
import io.github.archetom.crm.api.dto.response.PerformanceTargetListResponse;
import io.github.archetom.crm.api.dto.response.PerformanceTargetResponse;
import io.github.archetom.crm.api.facade.PerformanceTargetFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for tenant-managed member performance targets. */
@RestController
@RequestMapping("/api/v1/dashboard/performance-targets")
@RequiredArgsConstructor
@Tag(name = "Dashboard performance targets", description = "Versioned member monthly targets")
public class PerformanceTargetController {

    private final PerformanceTargetFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping
    @Operation(summary = "Query a member's monthly performance targets")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = PerformanceTargetListResponse.class)))
    public ResponseEntity<?> query(
            Authentication authentication,
            @ModelAttribute @Valid PerformanceTargetQueryRequest request) {
        return response(facade.query(callerMapper.from(authentication), request));
    }

    @PostMapping
    @Operation(summary = "Create a member monthly performance target")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = PerformanceTargetResponse.class)))
    public ResponseEntity<?> create(
            Authentication authentication,
            @RequestBody @Valid PerformanceTargetCreateRequest request) {
        return response(facade.create(callerMapper.from(authentication), request));
    }

    @PostMapping("/import")
    @Operation(summary = "Atomically import member monthly performance targets")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = PerformanceTargetImportResponse.class)))
    public ResponseEntity<?> importTargets(
            Authentication authentication,
            @RequestBody @Valid PerformanceTargetImportRequest request) {
        return response(facade.importTargets(callerMapper.from(authentication), request));
    }

    @PutMapping("/{targetId}")
    @Operation(summary = "Update a member monthly performance target")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = PerformanceTargetResponse.class)))
    public ResponseEntity<?> update(
            Authentication authentication,
            @PathVariable Long targetId,
            @RequestBody @Valid PerformanceTargetUpdateRequest request) {
        return response(facade.update(callerMapper.from(authentication), targetId, request));
    }

    @DeleteMapping("/{targetId}")
    @Operation(summary = "Delete a member monthly performance target")
    public ResponseEntity<?> delete(
            Authentication authentication,
            @PathVariable Long targetId,
            @RequestParam Long version) {
        return response(facade.delete(callerMapper.from(authentication), targetId, version));
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
