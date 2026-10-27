package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.InvoiceBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.InvoiceBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceBatchResetRequest;
import io.github.archetom.crm.api.dto.request.InvoiceCreateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceIssueRequest;
import io.github.archetom.crm.api.dto.request.InvoiceOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceQueryRequest;
import io.github.archetom.crm.api.dto.request.InvoiceUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceVersionRequest;
import io.github.archetom.crm.api.dto.response.InvoicePageResponse;
import io.github.archetom.crm.api.dto.response.InvoiceResponse;
import io.github.archetom.crm.api.facade.InvoiceFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@Validated
@Tag(name = "Invoices", description = "Tenant invoice application operations")
public class InvoiceController {
    private final InvoiceFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping("/query")
    @Operation(summary = "Query invoices")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = InvoicePageResponse.class)))
    public ResponseEntity<?> query(Authentication authentication,
                                   @RequestBody @Valid InvoiceQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @GetMapping("/{invoiceId}")
    @Operation(summary = "Get an invoice")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = InvoiceResponse.class)))
    public ResponseEntity<?> get(Authentication authentication,
                                 @PathVariable @Positive Long invoiceId) {
        return response(facade.get(caller(authentication), invoiceId));
    }

    @PostMapping
    @Operation(summary = "Create an invoice")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = InvoiceResponse.class)))
    public ResponseEntity<?> create(Authentication authentication,
                                    @RequestBody @Valid InvoiceCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @PutMapping("/{invoiceId}")
    @Operation(summary = "Update an invoice", description =
            "Omitted fields retain their current values; version is required.")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = InvoiceResponse.class)))
    public ResponseEntity<?> update(Authentication authentication,
                                    @PathVariable @Positive Long invoiceId,
                                    @RequestBody @Valid InvoiceUpdateRequest request) {
        return response(facade.update(caller(authentication), invoiceId, request));
    }

    @PatchMapping("/{invoiceId}")
    @Operation(summary = "Partially update an invoice", description =
            "Omitted fields retain their current values; only submitted non-null fields are changed."
                    + " Version is required.")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = InvoiceResponse.class)))
    public ResponseEntity<?> patch(Authentication authentication,
                                   @PathVariable @Positive Long invoiceId,
                                   @RequestBody @Valid InvoiceUpdateRequest request) {
        return response(facade.update(caller(authentication), invoiceId, request));
    }

    @PutMapping("/{invoiceId}/owner")
    @Operation(summary = "Change invoice owner")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = InvoiceResponse.class)))
    public ResponseEntity<?> updateOwner(Authentication authentication,
                                         @PathVariable @Positive Long invoiceId,
                                         @RequestBody @Valid InvoiceOwnerUpdateRequest request) {
        return response(facade.updateOwner(caller(authentication), invoiceId, request));
    }

    @PutMapping("/batch/owner")
    @Operation(summary = "Change invoice owners in a batch", description =
            "Atomically transfers at most 100 visible invoices after version, field, member, and scope checks.")
    public ResponseEntity<?> updateOwners(
            Authentication authentication,
            @RequestBody @Valid InvoiceBatchOwnerUpdateRequest request) {
        return response(facade.updateOwners(caller(authentication), request));
    }

    @PutMapping("/{invoiceId}/issue")
    @Operation(summary = "Register an invoice result")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = InvoiceResponse.class)))
    public ResponseEntity<?> issue(Authentication authentication,
                                   @PathVariable @Positive Long invoiceId,
                                   @RequestBody @Valid InvoiceIssueRequest request) {
        return response(facade.issue(caller(authentication), invoiceId, request));
    }

    @PutMapping("/{invoiceId}/reset")
    @Operation(summary = "Reset an invoice result")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = InvoiceResponse.class)))
    public ResponseEntity<?> reset(Authentication authentication,
                                   @PathVariable @Positive Long invoiceId,
                                   @RequestBody @Valid InvoiceVersionRequest request) {
        return response(facade.reset(
                caller(authentication), invoiceId, request.getVersion()));
    }

    @PutMapping("/batch/reset")
    @Operation(summary = "Reset invoice results in a batch", description =
            "Atomically resets at most 100 issued invoices after version and field checks.")
    public ResponseEntity<?> resetBatch(
            Authentication authentication,
            @RequestBody @Valid InvoiceBatchResetRequest request) {
        return response(facade.resetBatch(caller(authentication), request));
    }

    @DeleteMapping("/{invoiceId}")
    @Operation(summary = "Soft-delete an invoice")
    public ResponseEntity<?> delete(Authentication authentication,
                                    @PathVariable @Positive Long invoiceId,
                                    @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.delete(caller(authentication), invoiceId, version));
    }

    @PostMapping("/batch/delete")
    @Operation(summary = "Delete invoices in a batch", description =
            "Atomically soft-deletes at most 100 visible pending invoices after version checks.")
    public ResponseEntity<?> deleteBatch(
            Authentication authentication,
            @RequestBody @Valid InvoiceBatchDeleteRequest request) {
        return response(facade.deleteBatch(caller(authentication), request));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
