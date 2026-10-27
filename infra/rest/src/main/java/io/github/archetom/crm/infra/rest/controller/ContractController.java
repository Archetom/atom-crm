package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContractBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchCsvExportRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchCollaboratorAddRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchCollaboratorRemoveRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractCreateRequest;
import io.github.archetom.crm.api.dto.request.ContractOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractProductsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractQueryRequest;
import io.github.archetom.crm.api.dto.request.ContractUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractVersionRequest;
import io.github.archetom.crm.api.dto.response.ContractCollaboratorBaselineResponse;
import io.github.archetom.crm.api.dto.response.ContractListSummaryResponse;
import io.github.archetom.crm.api.dto.response.ContractPageResponse;
import io.github.archetom.crm.api.dto.response.ContractResponse;
import io.github.archetom.crm.api.dto.response.ContractProductsResponse;
import io.github.archetom.crm.api.facade.ContractFacade;
import io.github.archetom.crm.application.export.ContractCsvDocument;
import io.github.archetom.crm.application.service.ContractQueryService;
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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@Validated
@Tag(name = "Contracts", description = "Tenant contract operations")
public class ContractController {
    private final ContractFacade facade;
    private final ContractQueryService queryService;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping("/query")
    @Operation(summary = "Query contracts")
    @ApiResponse(responseCode = "200", description = "Visible field-filtered contract page",
            content = @Content(schema = @Schema(implementation = ContractPageResponse.class)))
    public ResponseEntity<?> query(Authentication authentication,
                                   @RequestBody @Valid ContractQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @PostMapping("/summary")
    @Operation(summary = "Summarize visible contracts")
    @ApiResponse(responseCode = "200", description = "Visible contract financial summary",
            content = @Content(schema = @Schema(implementation = ContractListSummaryResponse.class)))
    public ResponseEntity<?> summary(Authentication authentication,
                                     @RequestBody @Valid ContractQueryRequest request) {
        return response(facade.summary(caller(authentication), request));
    }

    @PostMapping(value = "/batch/export/csv", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Export selected contracts as CSV", description =
            "Exports at most 100 current-list rows after rechecking read/export scopes and "
                    + "their observed versions. Only unmasked fixed contract columns are written.")
    public ResponseEntity<InputStreamResource> exportSelectedCsv(
            Authentication authentication, @RequestBody @Valid ContractBatchCsvExportRequest request) {
        Result<ContractCsvDocument> result = queryService.exportSelected(caller(authentication), request);
        if (!result.isSuccess()) {
            throw new ResultResponseException(result);
        }
        ContractCsvDocument document = result.getData();
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"contracts-selected.csv\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(new InputStreamResource(document.openStream()));
        } catch (RuntimeException exception) {
            document.close();
            throw exception;
        }
    }

    @GetMapping("/{contractId}")
    @Operation(summary = "Get a contract")
    @ApiResponse(responseCode = "200", description = "Visible field-filtered contract",
            content = @Content(schema = @Schema(implementation = ContractResponse.class)))
    public ResponseEntity<?> get(Authentication authentication,
                                 @PathVariable @Positive Long contractId) {
        return response(facade.get(caller(authentication), contractId));
    }

    @PostMapping
    @Operation(summary = "Create a contract")
    @ApiResponse(responseCode = "200", description = "Created field-filtered contract",
            content = @Content(schema = @Schema(implementation = ContractResponse.class)))
    public ResponseEntity<?> create(Authentication authentication,
                                    @RequestBody @Valid ContractCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @PutMapping("/{contractId}")
    @Operation(summary = "Update a contract", description =
            "Omitted fields retain their current values; version is required.")
    @ApiResponse(responseCode = "200", description = "Updated field-filtered contract",
            content = @Content(schema = @Schema(implementation = ContractResponse.class)))
    public ResponseEntity<?> update(Authentication authentication,
                                    @PathVariable @Positive Long contractId,
                                    @RequestBody @Valid ContractUpdateRequest request) {
        return response(facade.update(caller(authentication), contractId, request));
    }

    @PatchMapping("/{contractId}")
    @Operation(summary = "Partially update a contract", description =
            "Omitted fields retain their current values; only submitted non-null fields are changed."
                    + " Version is required.")
    @ApiResponse(responseCode = "200", description = "Updated field-filtered contract",
            content = @Content(schema = @Schema(implementation = ContractResponse.class)))
    public ResponseEntity<?> patch(Authentication authentication,
                                   @PathVariable @Positive Long contractId,
                                   @RequestBody @Valid ContractUpdateRequest request) {
        return response(facade.update(caller(authentication), contractId, request));
    }

    @PutMapping("/{contractId}/owner")
    @Operation(summary = "Change contract owner")
    @ApiResponse(responseCode = "200", description = "Updated field-filtered contract",
            content = @Content(schema = @Schema(implementation = ContractResponse.class)))
    public ResponseEntity<?> updateOwner(Authentication authentication,
                                         @PathVariable @Positive Long contractId,
                                         @RequestBody @Valid ContractOwnerUpdateRequest request) {
        return response(facade.updateOwner(caller(authentication), contractId, request));
    }

    @PutMapping("/batch/owner")
    @Operation(summary = "Change contract owners in a batch", description =
            "Atomically transfers at most 100 visible contracts after version, field, member, and scope checks.")
    public ResponseEntity<?> updateOwners(
            Authentication authentication,
            @RequestBody @Valid ContractBatchOwnerUpdateRequest request) {
        return response(facade.updateOwners(caller(authentication), request));
    }

    @PutMapping("/batch/collaborators")
    @Operation(summary = "Add contract collaborators in a batch", description =
            "Atomically adds or updates collaborators on at most 100 visible contracts after version,"
                    + " member-state, and owner-scope checks. Existing collaborators not named by the"
                    + " request are retained.")
    public ResponseEntity<?> addCollaborators(
            Authentication authentication,
            @RequestBody @Valid ContractBatchCollaboratorAddRequest request) {
        return response(facade.addCollaborators(caller(authentication), request));
    }

    @DeleteMapping("/batch/collaborators")
    @Operation(summary = "Remove contract collaborators in a batch", description =
            "Atomically removes named non-owner collaborators from at most 100 visible contracts after"
                    + " version and owner-scope checks. Missing collaborators are a no-op.")
    public ResponseEntity<?> removeCollaborators(
            Authentication authentication,
            @RequestBody @Valid ContractBatchCollaboratorRemoveRequest request) {
        return response(facade.removeCollaborators(caller(authentication), request));
    }

    @GetMapping("/{contractId}/collaborators")
    @Operation(summary = "Get the authoritative contract collaborator baseline", description =
            "Requires contracts:assign owner scope and returns the complete replace baseline,"
                    + " its version, and the verified caller collaborator state.")
    @ApiResponse(responseCode = "200", description = "Contract collaborator baseline",
            content = @Content(schema = @Schema(
                    implementation = ContractCollaboratorBaselineResponse.class)))
    public ResponseEntity<?> collaborators(
            Authentication authentication, @PathVariable @Positive Long contractId) {
        return response(facade.collaborators(caller(authentication), contractId));
    }

    @GetMapping("/{contractId}/products")
    @Operation(summary = "Get complete contract product snapshots", description =
            "Requires readable unmasked contract product and product snapshot fields.")
    @ApiResponse(responseCode = "200", description = "Complete versioned contract product snapshot",
            content = @Content(schema = @Schema(implementation = ContractProductsResponse.class)))
    public ResponseEntity<?> products(
            Authentication authentication, @PathVariable @Positive Long contractId) {
        return response(facade.products(caller(authentication), contractId));
    }

    @PutMapping("/{contractId}/products")
    @Operation(summary = "Replace contract products", description =
            "Replaces all product lines using a versioned server snapshot; totals are recalculated"
                    + " server-side and cannot drop below committed invoice, receivable, or plan amounts.")
    @ApiResponse(responseCode = "200", description = "Updated complete contract product snapshot",
            content = @Content(schema = @Schema(implementation = ContractProductsResponse.class)))
    public ResponseEntity<?> replaceProducts(
            Authentication authentication, @PathVariable @Positive Long contractId,
            @RequestBody @Valid ContractProductsUpdateRequest request) {
        return response(facade.replaceProducts(caller(authentication), contractId, request));
    }

    @PutMapping("/{contractId}/collaborators")
    @Operation(summary = "Replace contract collaborators")
    @ApiResponse(responseCode = "200", description = "Updated field-filtered contract",
            content = @Content(schema = @Schema(implementation = ContractResponse.class)))
    public ResponseEntity<?> replaceCollaborators(
            Authentication authentication, @PathVariable @Positive Long contractId,
            @RequestBody @Valid ContractCollaboratorsUpdateRequest request) {
        return response(facade.replaceCollaborators(
                caller(authentication), contractId, request));
    }

    @DeleteMapping("/{contractId}/collaborators/me")
    @Operation(summary = "Exit contract collaboration")
    @ApiResponse(responseCode = "200", description = "Updated field-filtered contract",
            content = @Content(schema = @Schema(implementation = ContractResponse.class)))
    public ResponseEntity<?> exitCollaborator(
            Authentication authentication, @PathVariable @Positive Long contractId,
            @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.exitCollaborator(caller(authentication), contractId, version));
    }

    @PutMapping("/{contractId}/void")
    @Operation(summary = "Void a contract")
    @ApiResponse(responseCode = "200", description = "Updated field-filtered contract",
            content = @Content(schema = @Schema(implementation = ContractResponse.class)))
    public ResponseEntity<?> voidContract(
            Authentication authentication, @PathVariable @Positive Long contractId,
            @RequestBody @Valid ContractVersionRequest request) {
        return response(facade.voidContract(
                caller(authentication), contractId, request.getVersion()));
    }

    @DeleteMapping("/{contractId}")
    @Operation(summary = "Soft-delete a contract")
    public ResponseEntity<?> delete(
            Authentication authentication, @PathVariable @Positive Long contractId,
            @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.delete(caller(authentication), contractId, version));
    }

    @PostMapping("/batch/delete")
    @Operation(summary = "Delete contracts in a batch", description =
            "Atomically soft-deletes at most 100 visible contracts after version and dependency checks.")
    public ResponseEntity<?> deleteBatch(
            Authentication authentication,
            @RequestBody @Valid ContractBatchDeleteRequest request) {
        return response(facade.deleteBatch(caller(authentication), request));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
