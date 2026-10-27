package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContactBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.ContactBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactBatchTagRequest;
import io.github.archetom.crm.api.dto.request.ContactCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactCreateRequest;
import io.github.archetom.crm.api.dto.request.ContactCsvExportRequest;
import io.github.archetom.crm.api.dto.request.ContactDuplicateCheckRequest;
import io.github.archetom.crm.api.dto.request.ContactOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactParentUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactQueryRequest;
import io.github.archetom.crm.api.dto.request.ContactStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactStateUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactTagsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactUpdateRequest;
import io.github.archetom.crm.api.dto.response.ContactCollaboratorBaselineResponse;
import io.github.archetom.crm.api.dto.response.ContactPageResponse;
import io.github.archetom.crm.api.dto.response.ContactResponse;
import io.github.archetom.crm.api.facade.ContactFacade;
import io.github.archetom.crm.application.export.ContactCsvDocument;
import io.github.archetom.crm.application.service.ContactCsvExportService;
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
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
@Validated
@Tag(name = "Contacts", description = "Tenant-scoped contact aggregate operations")
public class ContactController {

    private final ContactFacade facade;
    private final ContactCsvExportService csvExportService;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping
    @Operation(summary = "Query visible contacts", responses = @ApiResponse(
            responseCode = "200", description = "Visible field-filtered contact page",
            content = @Content(schema = @Schema(implementation = ContactPageResponse.class))))
    public ResponseEntity<?> query(
            Authentication authentication, @Valid ContactQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @PostMapping(value = "/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Query visible contacts with body criteria", responses = @ApiResponse(
            responseCode = "200", description = "Visible field-filtered contact page",
            content = @Content(schema = @Schema(implementation = ContactPageResponse.class))))
    public ResponseEntity<?> queryWithBody(
            Authentication authentication, @RequestBody @Valid ContactQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @PostMapping
    @Operation(summary = "Create a contact")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ContactResponse.class)))
    public ResponseEntity<?> create(
            Authentication authentication, @RequestBody @Valid ContactCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @GetMapping("/tags")
    @Operation(summary = "List tags from visible contacts")
    public ResponseEntity<?> tags(Authentication authentication) {
        return response(facade.tags(caller(authentication)));
    }

    @PostMapping("/duplicate-check")
    @Operation(summary = "Check visible contacts for duplicate values")
    public ResponseEntity<?> duplicateCheck(
            Authentication authentication,
            @RequestBody @Valid ContactDuplicateCheckRequest request) {
        return response(facade.duplicateCheck(caller(authentication), request));
    }

    @PostMapping(value = "/export/csv", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Export bounded visible contacts as CSV")
    public ResponseEntity<InputStreamResource> exportCsv(
            Authentication authentication, @RequestBody @Valid ContactCsvExportRequest request) {
        Result<ContactCsvDocument> result = csvExportService.export(caller(authentication), request);
        if (!result.isSuccess()) {
            throw new ResultResponseException(result);
        }
        ContactCsvDocument document = result.getData();
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"contacts.csv\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(new InputStreamResource(document.openStream()));
        } catch (RuntimeException exception) {
            document.close();
            throw exception;
        }
    }

    @GetMapping("/nearby")
    @Operation(summary = "Query visible contacts near a coordinate")
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
    @Operation(summary = "List bounded visible contact summaries")
    public ResponseEntity<?> summaries(
            Authentication authentication, @RequestParam List<@Positive Long> ids) {
        return response(facade.summaries(caller(authentication), ids));
    }

    @PostMapping("/batch/tags")
    @Operation(summary = "Change tags for a bounded contact batch")
    public ResponseEntity<?> changeTags(
            Authentication authentication, @RequestBody @Valid ContactBatchTagRequest request) {
        return response(facade.changeTags(caller(authentication), request));
    }

    @PutMapping("/batch/owner")
    @Operation(summary = "Change the owner of a bounded contact batch atomically")
    public ResponseEntity<?> updateOwners(
            Authentication authentication,
            @RequestBody @Valid ContactBatchOwnerUpdateRequest request) {
        return response(facade.updateOwners(caller(authentication), request));
    }

    @PostMapping("/batch/delete")
    @Operation(summary = "Delete a bounded contact batch atomically")
    public ResponseEntity<?> deleteBatch(
            Authentication authentication, @RequestBody @Valid ContactBatchDeleteRequest request) {
        return response(facade.deleteBatch(caller(authentication), request));
    }

    @GetMapping("/{contactId}")
    @Operation(summary = "Get a visible contact")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ContactResponse.class)))
    public ResponseEntity<?> get(
            Authentication authentication, @PathVariable @Positive Long contactId) {
        return response(facade.get(caller(authentication), contactId));
    }

    @PutMapping("/{contactId}")
    @Operation(summary = "Update contact details")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ContactResponse.class)))
    public ResponseEntity<?> update(
            Authentication authentication, @PathVariable @Positive Long contactId,
            @RequestBody @Valid ContactUpdateRequest request) {
        return response(facade.update(caller(authentication), contactId, request));
    }

    @DeleteMapping("/{contactId}")
    @Operation(summary = "Soft-delete a contact")
    public ResponseEntity<?> delete(
            Authentication authentication, @PathVariable @Positive Long contactId,
            @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.delete(caller(authentication), contactId, version));
    }

    @PutMapping("/{contactId}/state")
    @Operation(summary = "Update lock and deal state")
    public ResponseEntity<?> updateState(
            Authentication authentication, @PathVariable @Positive Long contactId,
            @RequestBody @Valid ContactStateUpdateRequest request) {
        return response(facade.updateState(caller(authentication), contactId, request));
    }

    @PutMapping("/{contactId}/owner")
    @Operation(summary = "Change contact owner")
    public ResponseEntity<?> updateOwner(
            Authentication authentication, @PathVariable @Positive Long contactId,
            @RequestBody @Valid ContactOwnerUpdateRequest request) {
        return response(facade.updateOwner(caller(authentication), contactId, request));
    }

    @PutMapping("/{contactId}/tags")
    @Operation(summary = "Replace contact tags")
    public ResponseEntity<?> replaceTags(
            Authentication authentication, @PathVariable @Positive Long contactId,
            @RequestBody @Valid ContactTagsUpdateRequest request) {
        return response(facade.replaceTags(caller(authentication), contactId, request));
    }

    @GetMapping("/{contactId}/collaborators")
    @Operation(summary = "Get the authoritative contact collaborator baseline", description =
            "Requires contacts:assign owner scope and returns the complete replace baseline,"
                    + " its version, and the verified caller collaborator state.")
    @ApiResponse(responseCode = "200", description = "Contact collaborator baseline",
            content = @Content(schema = @Schema(
                    implementation = ContactCollaboratorBaselineResponse.class)))
    public ResponseEntity<?> collaborators(
            Authentication authentication, @PathVariable @Positive Long contactId) {
        return response(facade.collaborators(caller(authentication), contactId));
    }

    @PutMapping("/{contactId}/collaborators")
    @Operation(summary = "Replace contact collaborators")
    public ResponseEntity<?> replaceCollaborators(
            Authentication authentication, @PathVariable @Positive Long contactId,
            @RequestBody @Valid ContactCollaboratorsUpdateRequest request) {
        return response(facade.replaceCollaborators(caller(authentication), contactId, request));
    }

    @DeleteMapping("/{contactId}/collaborators/me")
    @Operation(summary = "Exit contact collaboration")
    public ResponseEntity<?> exitCollaborator(
            Authentication authentication, @PathVariable @Positive Long contactId,
            @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.exitCollaborator(caller(authentication), contactId, version));
    }

    @PutMapping("/{contactId}/parent")
    @Operation(summary = "Set or clear the parent contact")
    public ResponseEntity<?> updateParent(
            Authentication authentication, @PathVariable @Positive Long contactId,
            @RequestBody @Valid ContactParentUpdateRequest request) {
        return response(facade.updateParent(caller(authentication), contactId, request));
    }

    @GetMapping("/{contactId}/hierarchy")
    @Operation(summary = "Get a bounded visible contact hierarchy")
    public ResponseEntity<?> hierarchy(
            Authentication authentication, @PathVariable @Positive Long contactId) {
        return response(facade.hierarchy(caller(authentication), contactId));
    }

    @PutMapping("/{contactId}/star")
    @Operation(summary = "Set the caller's personal contact star")
    public ResponseEntity<?> setStarred(
            Authentication authentication, @PathVariable @Positive Long contactId,
            @RequestBody @Valid ContactStarUpdateRequest request) {
        return response(facade.setStarred(caller(authentication), contactId, request));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
