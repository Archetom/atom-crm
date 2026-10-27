package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.LeadBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadBatchTagRequest;
import io.github.archetom.crm.api.dto.request.LeadCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadConvertRequest;
import io.github.archetom.crm.api.dto.request.LeadCreateRequest;
import io.github.archetom.crm.api.dto.request.LeadCsvExportRequest;
import io.github.archetom.crm.api.dto.request.LeadDuplicateCheckRequest;
import io.github.archetom.crm.api.dto.request.LeadOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadParentUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadQueryRequest;
import io.github.archetom.crm.api.dto.request.LeadStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadStateUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadTagsUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadUpdateRequest;
import io.github.archetom.crm.api.dto.response.LeadCollaboratorBaselineResponse;
import io.github.archetom.crm.api.dto.response.LeadPageResponse;
import io.github.archetom.crm.api.dto.response.LeadResponse;
import io.github.archetom.crm.api.facade.LeadFacade;
import io.github.archetom.crm.application.export.LeadCsvDocument;
import io.github.archetom.crm.application.service.LeadCsvExportService;
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
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
@Validated
@Tag(name = "Leads", description = "Tenant-scoped lead aggregate operations")
public class LeadController {

    private final LeadFacade facade;
    private final LeadCsvExportService csvExportService;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping
    @Operation(summary = "Query visible leads", responses = @ApiResponse(
            responseCode = "200", description = "Visible field-filtered lead page",
            content = @Content(schema = @Schema(implementation = LeadPageResponse.class))))
    public ResponseEntity<?> query(
            Authentication authentication, @Valid LeadQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @PostMapping(value = "/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Query visible leads with body criteria", responses = @ApiResponse(
            responseCode = "200", description = "Visible field-filtered lead page",
            content = @Content(schema = @Schema(implementation = LeadPageResponse.class))))
    public ResponseEntity<?> queryWithBody(
            Authentication authentication, @RequestBody @Valid LeadQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @PostMapping(value = "/export/csv", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Export bounded visible leads as CSV")
    public ResponseEntity<InputStreamResource> exportCsv(
            Authentication authentication, @RequestBody @Valid LeadCsvExportRequest request) {
        Result<LeadCsvDocument> result = csvExportService.export(caller(authentication), request);
        if (!result.isSuccess()) {
            throw new ResultResponseException(result);
        }
        LeadCsvDocument document = result.getData();
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"leads.csv\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(new InputStreamResource(document.openStream()));
        } catch (RuntimeException exception) {
            document.close();
            throw exception;
        }
    }

    @PostMapping
    @Operation(summary = "Create a lead")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = LeadResponse.class)))
    public ResponseEntity<?> create(
            Authentication authentication, @RequestBody @Valid LeadCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @PostMapping("/{leadId}/convert")
    @Operation(summary = "Convert an active lead into a customer and contact")
    public ResponseEntity<?> convert(
            Authentication authentication, @PathVariable @Positive Long leadId,
            @RequestBody @Valid LeadConvertRequest request) {
        return response(facade.convert(caller(authentication), leadId, request));
    }

    @GetMapping("/tags")
    @Operation(summary = "List tags from visible leads")
    public ResponseEntity<?> tags(Authentication authentication) {
        return response(facade.tags(caller(authentication)));
    }

    @PostMapping("/duplicate-check")
    @Operation(summary = "Check visible leads for duplicate values")
    public ResponseEntity<?> duplicateCheck(
            Authentication authentication,
            @RequestBody @Valid LeadDuplicateCheckRequest request) {
        return response(facade.duplicateCheck(caller(authentication), request));
    }

    @GetMapping("/nearby")
    @Operation(summary = "Query visible leads near a coordinate")
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
    @Operation(summary = "List bounded visible lead summaries")
    public ResponseEntity<?> summaries(
            Authentication authentication, @RequestParam List<@Positive Long> ids) {
        return response(facade.summaries(caller(authentication), ids));
    }

    @PostMapping("/batch/tags")
    @Operation(summary = "Change tags for a bounded lead batch")
    public ResponseEntity<?> changeTags(
            Authentication authentication, @RequestBody @Valid LeadBatchTagRequest request) {
        return response(facade.changeTags(caller(authentication), request));
    }

    @PutMapping("/batch/owner")
    @Operation(summary = "Change the owner of a bounded lead batch atomically")
    public ResponseEntity<?> updateOwners(
            Authentication authentication,
            @RequestBody @Valid LeadBatchOwnerUpdateRequest request) {
        return response(facade.updateOwners(caller(authentication), request));
    }

    @PostMapping("/batch/delete")
    @Operation(summary = "Delete a bounded lead batch atomically")
    public ResponseEntity<?> deleteBatch(
            Authentication authentication, @RequestBody @Valid LeadBatchDeleteRequest request) {
        return response(facade.deleteBatch(caller(authentication), request));
    }

    @GetMapping("/{leadId}")
    @Operation(summary = "Get a visible lead")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = LeadResponse.class)))
    public ResponseEntity<?> get(
            Authentication authentication, @PathVariable @Positive Long leadId) {
        return response(facade.get(caller(authentication), leadId));
    }

    @PutMapping("/{leadId}")
    @Operation(summary = "Update lead details")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = LeadResponse.class)))
    public ResponseEntity<?> update(
            Authentication authentication, @PathVariable @Positive Long leadId,
            @RequestBody @Valid LeadUpdateRequest request) {
        return response(facade.update(caller(authentication), leadId, request));
    }

    @DeleteMapping("/{leadId}")
    @Operation(summary = "Soft-delete a lead")
    public ResponseEntity<?> delete(
            Authentication authentication, @PathVariable @Positive Long leadId,
            @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.delete(caller(authentication), leadId, version));
    }

    @PutMapping("/{leadId}/state")
    @Operation(summary = "Update lock and deal state")
    public ResponseEntity<?> updateState(
            Authentication authentication, @PathVariable @Positive Long leadId,
            @RequestBody @Valid LeadStateUpdateRequest request) {
        return response(facade.updateState(caller(authentication), leadId, request));
    }

    @PutMapping("/{leadId}/owner")
    @Operation(summary = "Change lead owner")
    public ResponseEntity<?> updateOwner(
            Authentication authentication, @PathVariable @Positive Long leadId,
            @RequestBody @Valid LeadOwnerUpdateRequest request) {
        return response(facade.updateOwner(caller(authentication), leadId, request));
    }

    @PutMapping("/{leadId}/tags")
    @Operation(summary = "Replace lead tags")
    public ResponseEntity<?> replaceTags(
            Authentication authentication, @PathVariable @Positive Long leadId,
            @RequestBody @Valid LeadTagsUpdateRequest request) {
        return response(facade.replaceTags(caller(authentication), leadId, request));
    }

    @GetMapping("/{leadId}/collaborators")
    @Operation(summary = "Get the authoritative lead collaborator baseline", description =
            "Requires leads:assign owner scope and returns the complete replace baseline,"
                    + " its version, and the verified caller collaborator state.")
    @ApiResponse(responseCode = "200", description = "Lead collaborator baseline",
            content = @Content(schema = @Schema(
                    implementation = LeadCollaboratorBaselineResponse.class)))
    public ResponseEntity<?> collaborators(
            Authentication authentication, @PathVariable @Positive Long leadId) {
        return response(facade.collaborators(caller(authentication), leadId));
    }

    @PutMapping("/{leadId}/collaborators")
    @Operation(summary = "Replace lead collaborators")
    public ResponseEntity<?> replaceCollaborators(
            Authentication authentication, @PathVariable @Positive Long leadId,
            @RequestBody @Valid LeadCollaboratorsUpdateRequest request) {
        return response(facade.replaceCollaborators(caller(authentication), leadId, request));
    }

    @DeleteMapping("/{leadId}/collaborators/me")
    @Operation(summary = "Exit lead collaboration")
    public ResponseEntity<?> exitCollaborator(
            Authentication authentication, @PathVariable @Positive Long leadId,
            @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.exitCollaborator(caller(authentication), leadId, version));
    }

    @PutMapping("/{leadId}/parent")
    @Operation(summary = "Set or clear the parent lead")
    public ResponseEntity<?> updateParent(
            Authentication authentication, @PathVariable @Positive Long leadId,
            @RequestBody @Valid LeadParentUpdateRequest request) {
        return response(facade.updateParent(caller(authentication), leadId, request));
    }

    @GetMapping("/{leadId}/hierarchy")
    @Operation(summary = "Get a bounded visible lead hierarchy")
    public ResponseEntity<?> hierarchy(
            Authentication authentication, @PathVariable @Positive Long leadId) {
        return response(facade.hierarchy(caller(authentication), leadId));
    }

    @PutMapping("/{leadId}/star")
    @Operation(summary = "Set the caller's personal lead star")
    public ResponseEntity<?> setStarred(
            Authentication authentication, @PathVariable @Positive Long leadId,
            @RequestBody @Valid LeadStarUpdateRequest request) {
        return response(facade.setStarred(caller(authentication), leadId, request));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
