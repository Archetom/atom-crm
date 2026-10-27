package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetBatchUpsertRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetCreateRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetImportRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetQueryRequest;
import io.github.archetom.crm.api.dto.request.DepartmentPerformanceTargetUpdateRequest;
import io.github.archetom.crm.api.dto.response.DepartmentPerformanceTargetListResponse;
import io.github.archetom.crm.api.dto.response.DepartmentPerformanceTargetResponse;
import io.github.archetom.crm.api.dto.response.PerformanceTargetImportResponse;
import io.github.archetom.crm.api.facade.DepartmentPerformanceTargetFacade;
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

/** HTTP adapter for tenant-managed department performance targets. */
@RestController
@RequestMapping("/api/v1/dashboard/department-performance-targets")
@RequiredArgsConstructor
@Tag(name = "Dashboard department performance targets",
        description = "Versioned annual, quarterly and monthly department targets")
public class DepartmentPerformanceTargetController {

    private final DepartmentPerformanceTargetFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping
    @Operation(summary = "Query a department's performance targets")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = DepartmentPerformanceTargetListResponse.class)))
    public ResponseEntity<?> query(
            Authentication authentication,
            @ModelAttribute @Valid DepartmentPerformanceTargetQueryRequest request) {
        return response(facade.query(callerMapper.from(authentication), request));
    }

    @PostMapping
    @Operation(summary = "Create a department performance target")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = DepartmentPerformanceTargetResponse.class)))
    public ResponseEntity<?> create(
            Authentication authentication,
            @RequestBody @Valid DepartmentPerformanceTargetCreateRequest request) {
        return response(facade.create(callerMapper.from(authentication), request));
    }

    @PutMapping("/{targetId}")
    @Operation(summary = "Update a department performance target")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = DepartmentPerformanceTargetResponse.class)))
    public ResponseEntity<?> update(
            Authentication authentication,
            @PathVariable Long targetId,
            @RequestBody @Valid DepartmentPerformanceTargetUpdateRequest request) {
        return response(facade.update(callerMapper.from(authentication), targetId, request));
    }

    @PutMapping("/batch")
    @Operation(summary = "Atomically set department performance targets in a batch")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = DepartmentPerformanceTargetListResponse.class)))
    public ResponseEntity<?> batchUpsert(
            Authentication authentication,
            @RequestBody @Valid DepartmentPerformanceTargetBatchUpsertRequest request) {
        return response(facade.batchUpsert(callerMapper.from(authentication), request));
    }

    @PostMapping("/import")
    @Operation(summary = "Atomically import department performance targets")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = PerformanceTargetImportResponse.class)))
    public ResponseEntity<?> importTargets(
            Authentication authentication,
            @RequestBody @Valid DepartmentPerformanceTargetImportRequest request) {
        return response(facade.importTargets(callerMapper.from(authentication), request));
    }

    @DeleteMapping("/{targetId}")
    @Operation(summary = "Delete a department performance target")
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
