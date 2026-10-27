package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OaFormCreateRequest;
import io.github.archetom.crm.api.dto.request.OaFormQueryRequest;
import io.github.archetom.crm.api.dto.request.OaFormStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OaFormUpdateRequest;
import io.github.archetom.crm.api.dto.response.OaFormResponse;
import io.github.archetom.crm.api.dto.response.OaFormConfigurationOptionsResponse;
import io.github.archetom.crm.api.dto.response.OaFormFieldCandidatePageResponse;
import io.github.archetom.crm.api.dto.response.OaFormPageResponse;
import io.github.archetom.crm.api.dto.response.ApprovalSelectionCandidatePageResponse;
import io.github.archetom.crm.api.facade.OaFormFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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

@RestController
@RequestMapping("/api/v1/oa/forms")
@RequiredArgsConstructor
@Tag(name = "OA forms", description = "Tenant OA request-form definitions")
public class OaFormController {

    private final OaFormFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping("/configuration-options")
    @Operation(summary = "List server-filtered OA form configuration options")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaFormConfigurationOptionsResponse.class)))
    public ResponseEntity<?> configurationOptions(Authentication authentication) {
        return response(facade.configurationOptions(caller(authentication)));
    }

    @GetMapping
    @Operation(summary = "Query OA forms")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaFormPageResponse.class)))
    public ResponseEntity<?> query(
            Authentication authentication,
            @ModelAttribute @Valid OaFormQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @GetMapping("/{formId}")
    @Operation(summary = "Get an OA form")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaFormResponse.class)))
    public ResponseEntity<?> get(
            Authentication authentication, @PathVariable Long formId) {
        return response(facade.get(caller(authentication), formId));
    }

    @GetMapping("/available")
    @Operation(summary = "List active OA forms available to the caller")
    @ApiResponse(responseCode = "200", content = @Content(
            array = @ArraySchema(schema = @Schema(implementation = OaFormResponse.class))))
    public ResponseEntity<?> available(Authentication authentication) {
        return response(facade.available(caller(authentication)));
    }

    @GetMapping("/available/{formId}")
    @Operation(summary = "Get an active OA form available to the caller")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaFormResponse.class)))
    public ResponseEntity<?> getAvailable(
            Authentication authentication, @PathVariable Long formId) {
        return response(facade.getAvailable(caller(authentication), formId));
    }

    @GetMapping("/available/{formId}/fields/{fieldCode}/candidates")
    @Operation(summary = "List server-authorized candidates for an available OA form field")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaFormFieldCandidatePageResponse.class)))
    public ResponseEntity<?> fieldCandidates(
            Authentication authentication, @PathVariable Long formId,
            @PathVariable String fieldCode, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return response(facade.fieldCandidates(caller(authentication), formId,
                fieldCode, keyword, page, size));
    }

    @GetMapping("/available/{formId}/approval-selections/{selectionCode}/candidates")
    @Operation(summary = "List server-authorized candidates for an available OA approval selection")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ApprovalSelectionCandidatePageResponse.class)))
    public ResponseEntity<?> approvalSelectionCandidates(
            Authentication authentication, @PathVariable Long formId,
            @PathVariable String selectionCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size) {
        return response(facade.approvalSelectionCandidates(caller(authentication), formId,
                selectionCode, keyword, page, size));
    }

    @PostMapping
    @Operation(summary = "Create an inactive OA form")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaFormResponse.class)))
    public ResponseEntity<?> create(
            Authentication authentication,
            @RequestBody @Valid OaFormCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @PutMapping("/{formId}")
    @Operation(summary = "Update an OA form")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaFormResponse.class)))
    public ResponseEntity<?> update(
            Authentication authentication, @PathVariable Long formId,
            @RequestBody @Valid OaFormUpdateRequest request) {
        return response(facade.update(caller(authentication), formId, request));
    }

    @PutMapping("/{formId}/status")
    @Operation(summary = "Activate or deactivate an OA form")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaFormResponse.class)))
    public ResponseEntity<?> updateStatus(
            Authentication authentication, @PathVariable Long formId,
            @RequestBody @Valid OaFormStatusUpdateRequest request) {
        return response(facade.updateStatus(caller(authentication), formId, request));
    }

    @DeleteMapping("/{formId}")
    @Operation(summary = "Soft-delete an inactive OA form")
    public ResponseEntity<?> delete(
            Authentication authentication, @PathVariable Long formId,
            @RequestParam Long version) {
        return response(facade.delete(caller(authentication), formId, version));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
