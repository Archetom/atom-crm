package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CustomerBatchTagRequest;
import io.github.archetom.crm.api.dto.request.CustomerCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerCreateRequest;
import io.github.archetom.crm.api.dto.request.CustomerCsvExportRequest;
import io.github.archetom.crm.api.dto.request.CustomerDuplicateCheckRequest;
import io.github.archetom.crm.api.dto.request.CustomerOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerParentUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerQueryRequest;
import io.github.archetom.crm.api.dto.request.CustomerStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerStateUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerTagsUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerUpdateRequest;
import io.github.archetom.crm.api.dto.response.CustomerCollaboratorBaselineResponse;
import io.github.archetom.crm.api.dto.response.CustomerPageResponse;
import io.github.archetom.crm.api.dto.response.CustomerResponse;
import io.github.archetom.crm.api.facade.CustomerFacade;
import io.github.archetom.crm.api.facade.CustomerBusinessOverviewFacade;
import io.github.archetom.crm.application.export.CustomerCsvDocument;
import io.github.archetom.crm.application.service.CustomerCsvExportService;
import io.github.archetom.crm.infra.rest.advice.ResultResponseException;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.InputStreamResource;
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

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Validated
@Tag(name = "Customers", description = "Tenant-scoped customer aggregate operations")
public class CustomerController {

    private final CustomerFacade facade;
    private final CustomerBusinessOverviewFacade businessOverviewFacade;
    private final CustomerCsvExportService csvExportService;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping
    @Operation(summary = "Query visible customers")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = CustomerPageResponse.class)))
    public ResponseEntity<?> query(
            Authentication authentication, @Valid CustomerQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @PostMapping(value = "/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Query visible customers with body criteria")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = CustomerPageResponse.class)))
    public ResponseEntity<?> queryWithBody(
            Authentication authentication, @RequestBody @Valid CustomerQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @PostMapping
    @Operation(summary = "Create a customer")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = CustomerResponse.class)))
    public ResponseEntity<?> create(
            Authentication authentication, @RequestBody @Valid CustomerCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @GetMapping("/tags")
    @Operation(summary = "List tags from visible customers")
    public ResponseEntity<?> tags(Authentication authentication) {
        return response(facade.tags(caller(authentication)));
    }

    @PostMapping("/duplicate-check")
    @Operation(summary = "Check visible customers for duplicate values")
    public ResponseEntity<?> duplicateCheck(
            Authentication authentication,
            @RequestBody @Valid CustomerDuplicateCheckRequest request) {
        return response(facade.duplicateCheck(caller(authentication), request));
    }

    @PostMapping(value = "/export/csv", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Export bounded visible customers as CSV")
    public ResponseEntity<InputStreamResource> exportCsv(
            Authentication authentication, @RequestBody @Valid CustomerCsvExportRequest request) {
        Result<CustomerCsvDocument> result = csvExportService.export(caller(authentication), request);
        if (!result.isSuccess()) {
            throw new ResultResponseException(result);
        }
        CustomerCsvDocument document = result.getData();
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"customers.csv\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(new InputStreamResource(document.openStream()));
        } catch (RuntimeException exception) {
            document.close();
            throw exception;
        }
    }

    @GetMapping("/nearby")
    @Operation(summary = "Query visible customers near a coordinate")
    public ResponseEntity<?> nearby(
            Authentication authentication,
            @RequestParam @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @RequestParam @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @RequestParam @NotNull @Min(1) @Max(50000) Integer radiusMeters,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) Integer size) {
        return response(facade.nearby(caller(authentication), longitude, latitude,
                radiusMeters, page, size));
    }

    @GetMapping("/summaries")
    @Operation(summary = "List bounded visible customer summaries")
    public ResponseEntity<?> summaries(
            Authentication authentication, @RequestParam List<@Positive Long> ids) {
        return response(facade.summaries(caller(authentication), ids));
    }

    @PostMapping("/batch/tags")
    @Operation(summary = "Change tags for a bounded customer batch")
    public ResponseEntity<?> changeTags(
            Authentication authentication, @RequestBody @Valid CustomerBatchTagRequest request) {
        return response(facade.changeTags(caller(authentication), request));
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Get a visible customer")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = CustomerResponse.class)))
    public ResponseEntity<?> get(
            Authentication authentication, @PathVariable @Positive Long customerId) {
        return response(facade.get(caller(authentication), customerId));
    }

    @GetMapping("/{customerId}/business-overview")
    @Operation(summary = "Get the fixed permission-aware business overview for a customer")
    public ResponseEntity<?> businessOverview(
            Authentication authentication, @PathVariable @Positive Long customerId) {
        return response(businessOverviewFacade.get(caller(authentication), customerId));
    }

    @PutMapping("/{customerId}")
    @Operation(summary = "Update customer details")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = CustomerResponse.class)))
    public ResponseEntity<?> update(
            Authentication authentication, @PathVariable @Positive Long customerId,
            @RequestBody @Valid CustomerUpdateRequest request) {
        return response(facade.update(caller(authentication), customerId, request));
    }

    @DeleteMapping("/{customerId}")
    @Operation(summary = "Soft-delete a customer")
    public ResponseEntity<?> delete(
            Authentication authentication, @PathVariable @Positive Long customerId,
            @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.delete(caller(authentication), customerId, version));
    }

    @PutMapping("/{customerId}/state")
    @Operation(summary = "Update lock and deal state")
    public ResponseEntity<?> updateState(
            Authentication authentication, @PathVariable @Positive Long customerId,
            @RequestBody @Valid CustomerStateUpdateRequest request) {
        return response(facade.updateState(caller(authentication), customerId, request));
    }

    @PutMapping("/{customerId}/owner")
    @Operation(summary = "Change customer owner")
    public ResponseEntity<?> updateOwner(
            Authentication authentication, @PathVariable @Positive Long customerId,
            @RequestBody @Valid CustomerOwnerUpdateRequest request) {
        return response(facade.updateOwner(caller(authentication), customerId, request));
    }

    @PutMapping("/{customerId}/tags")
    @Operation(summary = "Replace customer tags")
    public ResponseEntity<?> replaceTags(
            Authentication authentication, @PathVariable @Positive Long customerId,
            @RequestBody @Valid CustomerTagsUpdateRequest request) {
        return response(facade.replaceTags(caller(authentication), customerId, request));
    }

    @GetMapping("/{customerId}/collaborators")
    @Operation(summary = "Get the authoritative customer collaborator baseline", description =
            "Requires customers:assign owner scope and returns the complete replace baseline,"
                    + " its version, and the verified caller collaborator state.")
    @ApiResponse(responseCode = "200", description = "Customer collaborator baseline",
            content = @Content(schema = @Schema(
                    implementation = CustomerCollaboratorBaselineResponse.class)))
    public ResponseEntity<?> collaborators(
            Authentication authentication, @PathVariable @Positive Long customerId) {
        return response(facade.collaborators(caller(authentication), customerId));
    }

    @PutMapping("/{customerId}/collaborators")
    @Operation(summary = "Replace customer collaborators")
    public ResponseEntity<?> replaceCollaborators(
            Authentication authentication, @PathVariable @Positive Long customerId,
            @RequestBody @Valid CustomerCollaboratorsUpdateRequest request) {
        return response(facade.replaceCollaborators(caller(authentication), customerId, request));
    }

    @DeleteMapping("/{customerId}/collaborators/me")
    @Operation(summary = "Exit customer collaboration")
    public ResponseEntity<?> exitCollaborator(
            Authentication authentication, @PathVariable @Positive Long customerId,
            @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.exitCollaborator(caller(authentication), customerId, version));
    }

    @PutMapping("/{customerId}/parent")
    @Operation(summary = "Set or clear the parent customer")
    public ResponseEntity<?> updateParent(
            Authentication authentication, @PathVariable @Positive Long customerId,
            @RequestBody @Valid CustomerParentUpdateRequest request) {
        return response(facade.updateParent(caller(authentication), customerId, request));
    }

    @GetMapping("/{customerId}/hierarchy")
    @Operation(summary = "Get a bounded visible customer hierarchy")
    public ResponseEntity<?> hierarchy(
            Authentication authentication, @PathVariable @Positive Long customerId) {
        return response(facade.hierarchy(caller(authentication), customerId));
    }

    @PutMapping("/{customerId}/star")
    @Operation(summary = "Set the caller's personal customer star")
    public ResponseEntity<?> setStarred(
            Authentication authentication, @PathVariable @Positive Long customerId,
            @RequestBody @Valid CustomerStarUpdateRequest request) {
        return response(facade.setStarred(caller(authentication), customerId, request));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
