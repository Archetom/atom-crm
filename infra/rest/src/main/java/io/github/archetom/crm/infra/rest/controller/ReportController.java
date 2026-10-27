package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReportRankingRequest;
import io.github.archetom.crm.api.dto.request.ReportTrendRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionCreateRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionQueryRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionRunRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionUpdateRequest;
import io.github.archetom.crm.api.dto.response.CoreReportResponse;
import io.github.archetom.crm.api.dto.response.ReportCatalogResponse;
import io.github.archetom.crm.api.dto.response.ReportDefinitionResponse;
import io.github.archetom.crm.api.dto.response.ReportDefinitionPageResponse;
import io.github.archetom.crm.api.dto.response.ReportDefinitionRunResponse;
import io.github.archetom.crm.api.dto.response.ReportRankingResponse;
import io.github.archetom.crm.api.dto.response.ReportTrendResponse;
import io.github.archetom.crm.api.facade.ReportFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Permission-filtered commercial reports")
public class ReportController {

    private final ReportFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping("/core")
    @Operation(summary = "Get the core commercial report")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = CoreReportResponse.class)))
    public ResponseEntity<?> core(Authentication authentication) {
        return response(facade.core(caller(authentication)));
    }

    @PostMapping("/trends")
    @Operation(summary = "Query one fixed commercial trend")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReportTrendResponse.class)))
    public ResponseEntity<?> trend(
            Authentication authentication,
            @RequestBody @Valid ReportTrendRequest request) {
        return response(facade.trend(caller(authentication), request));
    }

    @PostMapping("/rankings")
    @Operation(summary = "Query one fixed owner ranking")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReportRankingResponse.class)))
    public ResponseEntity<?> ranking(
            Authentication authentication,
            @RequestBody @Valid ReportRankingRequest request) {
        return response(facade.ranking(caller(authentication), request));
    }

    @GetMapping("/catalog")
    @Operation(summary = "Get the current member's report field catalog")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReportCatalogResponse.class)))
    public ResponseEntity<?> catalog(
            Authentication authentication, @RequestParam(required = false) String objectType) {
        return response(facade.catalog(caller(authentication), objectType));
    }

    @PostMapping("/definitions/query")
    @Operation(summary = "Query the current member's report definitions")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReportDefinitionPageResponse.class)))
    public ResponseEntity<?> queryDefinitions(
            Authentication authentication,
            @RequestBody @Valid ReportDefinitionQueryRequest request) {
        return response(facade.queryDefinitions(caller(authentication), request));
    }

    @GetMapping("/definitions/{definitionId}")
    @Operation(summary = "Get one personal report definition")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReportDefinitionResponse.class)))
    public ResponseEntity<?> getDefinition(
            Authentication authentication, @PathVariable Long definitionId) {
        return response(facade.getDefinition(caller(authentication), definitionId));
    }

    @PostMapping("/definitions")
    @Operation(summary = "Create a personal report definition")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReportDefinitionResponse.class)))
    public ResponseEntity<?> createDefinition(
            Authentication authentication,
            @RequestBody @Valid ReportDefinitionCreateRequest request) {
        return response(facade.createDefinition(caller(authentication), request));
    }

    @PutMapping("/definitions/{definitionId}")
    @Operation(summary = "Replace a personal report definition")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReportDefinitionResponse.class)))
    public ResponseEntity<?> updateDefinition(
            Authentication authentication, @PathVariable Long definitionId,
            @RequestBody @Valid ReportDefinitionUpdateRequest request) {
        return response(facade.updateDefinition(caller(authentication), definitionId, request));
    }

    @DeleteMapping("/definitions/{definitionId}")
    @Operation(summary = "Soft-delete a personal report definition")
    public ResponseEntity<?> deleteDefinition(
            Authentication authentication, @PathVariable Long definitionId,
            @RequestParam Long version) {
        return response(facade.deleteDefinition(caller(authentication), definitionId, version));
    }

    @PostMapping("/definitions/{definitionId}/run")
    @Operation(summary = "Run a personal report definition")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReportDefinitionRunResponse.class)))
    public ResponseEntity<?> runDefinition(
            Authentication authentication, @PathVariable Long definitionId,
            @RequestBody @Valid ReportDefinitionRunRequest request) {
        return response(facade.runDefinition(caller(authentication), definitionId, request));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
