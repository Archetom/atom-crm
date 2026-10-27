package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReceivableBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.ReceivableBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableQueryRequest;
import io.github.archetom.crm.api.dto.request.ReceivableUpdateRequest;
import io.github.archetom.crm.api.dto.response.ReceivableListSummaryResponse;
import io.github.archetom.crm.api.dto.response.ReceivablePageResponse;
import io.github.archetom.crm.api.dto.response.ReceivableResponse;
import io.github.archetom.crm.api.facade.ReceivableFacade;
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
@RequestMapping("/api/v1/receivables")
@RequiredArgsConstructor
@Validated
@Tag(name = "Receivables", description = "Tenant receivable operations")
public class ReceivableController {
    private final ReceivableFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping("/query")
    @Operation(summary = "Query receivables")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReceivablePageResponse.class)))
    public ResponseEntity<?> query(Authentication authentication,
                                   @RequestBody @Valid ReceivableQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @PostMapping("/summary")
    @Operation(summary = "Summarize visible receivables")
    @ApiResponse(responseCode = "200", description = "Visible receivable amount summary",
            content = @Content(schema = @Schema(implementation = ReceivableListSummaryResponse.class)))
    public ResponseEntity<?> summary(Authentication authentication,
                                     @RequestBody @Valid ReceivableQueryRequest request) {
        return response(facade.summary(caller(authentication), request));
    }

    @GetMapping("/{receivableId}")
    @Operation(summary = "Get a receivable")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReceivableResponse.class)))
    public ResponseEntity<?> get(Authentication authentication,
                                 @PathVariable @Positive Long receivableId) {
        return response(facade.get(caller(authentication), receivableId));
    }

    @PostMapping
    @Operation(summary = "Create a receivable")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReceivableResponse.class)))
    public ResponseEntity<?> create(Authentication authentication,
                                    @RequestBody @Valid ReceivableCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @PutMapping("/{receivableId}")
    @Operation(summary = "Update a receivable", description =
            "Omitted fields retain their current values; version is required.")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReceivableResponse.class)))
    public ResponseEntity<?> update(Authentication authentication,
                                    @PathVariable @Positive Long receivableId,
                                    @RequestBody @Valid ReceivableUpdateRequest request) {
        return response(facade.update(caller(authentication), receivableId, request));
    }

    @PatchMapping("/{receivableId}")
    @Operation(summary = "Partially update a receivable", description =
            "Omitted fields retain their current values; only submitted non-null fields are changed."
                    + " Version is required.")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReceivableResponse.class)))
    public ResponseEntity<?> patch(Authentication authentication,
                                   @PathVariable @Positive Long receivableId,
                                   @RequestBody @Valid ReceivableUpdateRequest request) {
        return response(facade.update(caller(authentication), receivableId, request));
    }

    @PutMapping("/{receivableId}/owner")
    @Operation(summary = "Change receivable owner")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ReceivableResponse.class)))
    public ResponseEntity<?> updateOwner(Authentication authentication,
                                         @PathVariable @Positive Long receivableId,
                                         @RequestBody @Valid ReceivableOwnerUpdateRequest request) {
        return response(facade.updateOwner(caller(authentication), receivableId, request));
    }

    @PutMapping("/batch/owner")
    @Operation(summary = "Change receivable owners in a batch", description =
            "Atomically transfers at most 100 visible receivables after version, field, member, and scope checks.")
    public ResponseEntity<?> updateOwners(
            Authentication authentication,
            @RequestBody @Valid ReceivableBatchOwnerUpdateRequest request) {
        return response(facade.updateOwners(caller(authentication), request));
    }

    @DeleteMapping("/{receivableId}")
    @Operation(summary = "Soft-delete a receivable")
    public ResponseEntity<?> delete(Authentication authentication,
                                    @PathVariable @Positive Long receivableId,
                                    @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.delete(caller(authentication), receivableId, version));
    }

    @PostMapping("/batch/delete")
    @Operation(summary = "Delete receivables in a batch", description =
            "Atomically soft-deletes at most 100 visible receivables after version checks.")
    public ResponseEntity<?> deleteBatch(
            Authentication authentication,
            @RequestBody @Valid ReceivableBatchDeleteRequest request) {
        return response(facade.deleteBatch(caller(authentication), request));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
