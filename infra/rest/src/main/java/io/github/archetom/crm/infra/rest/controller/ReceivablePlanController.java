package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReceivablePlanBatchCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanCsvExportRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanQueryRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanUpdateRequest;
import io.github.archetom.crm.api.dto.response.ReceivablePlanListSummaryResponse;
import io.github.archetom.crm.api.dto.response.ReceivablePlanPageResponse;
import io.github.archetom.crm.api.dto.response.ReceivablePlanResponse;
import io.github.archetom.crm.api.facade.ReceivablePlanFacade;
import io.github.archetom.crm.application.export.ReceivablePlanCsvDocument;
import io.github.archetom.crm.application.service.ReceivablePlanQueryService;
import io.github.archetom.crm.infra.rest.advice.ResultResponseException;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/receivable-plans")
@RequiredArgsConstructor
@Validated
@Tag(name = "Receivable plans", description = "Tenant receivable plan operations")
public class ReceivablePlanController {
    private final ReceivablePlanFacade facade;
    private final ReceivablePlanQueryService queryService;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping("/query")
    @Operation(summary = "Query receivable plans")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReceivablePlanPageResponse.class)))
    public ResponseEntity<?> query(Authentication authentication,
                                   @RequestBody @Valid ReceivablePlanQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @PostMapping("/summary")
    @Operation(summary = "Summarize visible receivable plans")
    @ApiResponse(responseCode = "200", content = @Content(schema =
            @Schema(implementation = ReceivablePlanListSummaryResponse.class)))
    public ResponseEntity<?> summary(Authentication authentication,
                                     @RequestBody @Valid ReceivablePlanQueryRequest request) {
        return response(facade.summary(caller(authentication), request));
    }

    @PostMapping(value = "/export/csv", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Export bounded visible receivable plans as CSV")
    public ResponseEntity<InputStreamResource> exportCsv(
            Authentication authentication, @RequestBody @Valid ReceivablePlanCsvExportRequest request) {
        Result<ReceivablePlanCsvDocument> result = queryService.export(caller(authentication), request);
        if (!result.isSuccess()) {
            throw new ResultResponseException(result);
        }
        ReceivablePlanCsvDocument document = result.getData();
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"receivable-plans.csv\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(new InputStreamResource(document.openStream()));
        } catch (RuntimeException exception) {
            document.close();
            throw exception;
        }
    }

    @GetMapping("/{planId}")
    @Operation(summary = "Get a receivable plan")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReceivablePlanResponse.class)))
    public ResponseEntity<?> get(Authentication authentication,
                                 @PathVariable @Positive Long planId) {
        return response(facade.get(caller(authentication), planId));
    }

    @GetMapping("/available")
    @Operation(summary = "List receivable plans available to a contract")
    @ApiResponse(responseCode = "200", content = @Content(
            array = @ArraySchema(schema = @Schema(implementation = ReceivablePlanResponse.class))))
    public ResponseEntity<?> available(Authentication authentication,
                                       @RequestParam @Positive Long contractId) {
        return response(facade.available(caller(authentication), contractId));
    }

    @PostMapping
    @Operation(summary = "Create a receivable plan")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReceivablePlanResponse.class)))
    public ResponseEntity<?> create(Authentication authentication,
                                    @RequestBody @Valid ReceivablePlanCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @PostMapping("/batch")
    @Operation(summary = "Create receivable plans in a batch")
    @ApiResponse(responseCode = "200", content = @Content(
            array = @ArraySchema(schema = @Schema(implementation = ReceivablePlanResponse.class))))
    public ResponseEntity<?> createBatch(Authentication authentication,
                                         @RequestBody @Valid ReceivablePlanBatchCreateRequest request) {
        return response(facade.createBatch(caller(authentication), request));
    }

    @PutMapping("/{planId}")
    @Operation(summary = "Update a receivable plan", description =
            "Omitted fields retain their current values; version is required.")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReceivablePlanResponse.class)))
    public ResponseEntity<?> update(Authentication authentication,
                                    @PathVariable @Positive Long planId,
                                    @RequestBody @Valid ReceivablePlanUpdateRequest request) {
        return response(facade.update(caller(authentication), planId, request));
    }

    @PatchMapping("/{planId}")
    @Operation(summary = "Partially update a receivable plan", description =
            "Omitted fields retain their current values; only submitted non-null fields are changed."
                    + " Version is required.")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReceivablePlanResponse.class)))
    public ResponseEntity<?> patch(Authentication authentication,
                                   @PathVariable @Positive Long planId,
                                   @RequestBody @Valid ReceivablePlanUpdateRequest request) {
        return response(facade.update(caller(authentication), planId, request));
    }

    @DeleteMapping("/{planId}")
    @Operation(summary = "Soft-delete a receivable plan")
    public ResponseEntity<?> delete(Authentication authentication,
                                    @PathVariable @Positive Long planId,
                                    @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.delete(caller(authentication), planId, version));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
