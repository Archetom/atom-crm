package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ProductAvailabilityUpdateRequest;
import io.github.archetom.crm.api.dto.request.ProductCreateRequest;
import io.github.archetom.crm.api.dto.request.ProductCsvExportRequest;
import io.github.archetom.crm.api.dto.request.ProductOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ProductQueryRequest;
import io.github.archetom.crm.api.dto.request.ProductUpdateRequest;
import io.github.archetom.crm.api.dto.response.ProductEditableBaselineResponse;
import io.github.archetom.crm.api.dto.response.ProductPageResponse;
import io.github.archetom.crm.api.dto.response.ProductResponse;
import io.github.archetom.crm.api.facade.ProductFacade;
import io.github.archetom.crm.application.export.ProductCsvDocument;
import io.github.archetom.crm.application.service.ProductCsvExportService;
import io.github.archetom.crm.infra.rest.advice.ResultResponseException;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Validated
@Tag(name = "Products", description = "Tenant product catalog operations")
public class ProductController {

    private final ProductFacade facade;
    private final ProductCsvExportService csvExportService;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping("/query")
    @Operation(summary = "Query products")
    @ApiResponse(responseCode = "200", description = "Visible field-filtered product page",
            content = @Content(schema = @Schema(implementation = ProductPageResponse.class)))
    public ResponseEntity<?> query(
            Authentication authentication,
            @RequestBody @Valid ProductQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @PostMapping(value = "/export/csv", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Export bounded visible products as CSV")
    public ResponseEntity<InputStreamResource> exportCsv(
            Authentication authentication, @RequestBody @Valid ProductCsvExportRequest request) {
        Result<ProductCsvDocument> result = csvExportService.export(caller(authentication), request);
        if (!result.isSuccess()) {
            throw new ResultResponseException(result);
        }
        ProductCsvDocument document = result.getData();
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"products.csv\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(new InputStreamResource(document.openStream()));
        } catch (RuntimeException exception) {
            document.close();
            throw exception;
        }
    }

    @PostMapping
    @Operation(summary = "Create a product")
    @ApiResponse(responseCode = "200", description = "Product created",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    public ResponseEntity<?> create(
            Authentication authentication,
            @RequestBody @Valid ProductCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @GetMapping("/{productId}/editable-baseline")
    @Operation(summary = "Get an authoritative complete product replacement baseline",
            description = "Returns business values only when every replace field is readable, "
                    + "unmasked, and writable for the caller.")
    @ApiResponse(responseCode = "200", description = "Product editable baseline",
            content = @Content(schema = @Schema(
                    implementation = ProductEditableBaselineResponse.class)))
    public ResponseEntity<?> editableBaseline(
            Authentication authentication,
            @PathVariable @Positive Long productId) {
        return response(facade.editableBaseline(caller(authentication), productId));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get a product")
    @ApiResponse(responseCode = "200", description = "Product found",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    public ResponseEntity<?> get(
            Authentication authentication,
            @PathVariable @Positive Long productId) {
        return response(facade.get(caller(authentication), productId));
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Update a product")
    @ApiResponse(responseCode = "200", description = "Product updated",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    public ResponseEntity<?> update(
            Authentication authentication,
            @PathVariable @Positive Long productId,
            @RequestBody @Valid ProductUpdateRequest request) {
        return response(facade.update(caller(authentication), productId, request));
    }

    @PutMapping("/{productId}/availability")
    @Operation(summary = "Change product availability")
    @ApiResponse(responseCode = "200", description = "Product availability updated",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    public ResponseEntity<?> updateAvailability(
            Authentication authentication,
            @PathVariable @Positive Long productId,
            @RequestBody @Valid ProductAvailabilityUpdateRequest request) {
        return response(facade.updateAvailability(
                caller(authentication), productId, request));
    }

    @PutMapping("/{productId}/owner")
    @Operation(summary = "Change product owner")
    @ApiResponse(responseCode = "200", description = "Product owner updated",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    public ResponseEntity<?> updateOwner(
            Authentication authentication,
            @PathVariable @Positive Long productId,
            @RequestBody @Valid ProductOwnerUpdateRequest request) {
        return response(facade.updateOwner(caller(authentication), productId, request));
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "Soft-delete a product")
    public ResponseEntity<?> delete(
            Authentication authentication,
            @PathVariable @Positive Long productId,
            @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.delete(caller(authentication), productId, version));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
