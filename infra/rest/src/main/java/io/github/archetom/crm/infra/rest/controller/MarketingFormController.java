package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.MarketingFormCreateRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormQueryRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionQueryRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionTrendRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormUpdateRequest;
import io.github.archetom.crm.api.dto.response.MarketingFormResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormConfigurationOptionsResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormPageResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormSubmissionLinkPageResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormSubmissionTrendResponse;
import io.github.archetom.crm.api.facade.MarketingFormFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
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
@RequestMapping("/api/v1/marketing-forms")
@RequiredArgsConstructor
@Tag(name = "Marketing forms", description = "Tenant marketing-form configuration")
public class MarketingFormController {

    private final MarketingFormFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping("/configuration-options")
    @Operation(summary = "List server-authorized marketing-form field options")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = MarketingFormConfigurationOptionsResponse.class)))
    public ResponseEntity<?> configurationOptions(
            Authentication authentication,
            @Parameter(description = "Existing form ID; its persisted target is used when present")
            @RequestParam(required = false) Long formId,
            @Parameter(description = "Target for a new form", schema = @Schema(
                    allowableValues = {"CUSTOMER", "LEAD"}))
            @RequestParam(required = false) String targetObjectType) {
        return response(facade.configurationOptions(
                caller(authentication), formId, targetObjectType));
    }

    @GetMapping
    @Operation(summary = "Query marketing forms")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = MarketingFormPageResponse.class)))
    public ResponseEntity<?> query(
            Authentication authentication,
            @ModelAttribute @Valid MarketingFormQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @GetMapping("/{formId}")
    @Operation(summary = "Get a marketing form")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = MarketingFormResponse.class)))
    public ResponseEntity<?> get(
            Authentication authentication, @PathVariable Long formId) {
        return response(facade.get(caller(authentication), formId));
    }

    @PostMapping
    @Operation(summary = "Create an inactive marketing form")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = MarketingFormResponse.class)))
    public ResponseEntity<?> create(
            Authentication authentication,
            @RequestBody @Valid MarketingFormCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @PutMapping("/{formId}")
    @Operation(summary = "Update marketing-form configuration and fields")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = MarketingFormResponse.class)))
    public ResponseEntity<?> update(
            Authentication authentication, @PathVariable Long formId,
            @RequestBody @Valid MarketingFormUpdateRequest request) {
        return response(facade.update(caller(authentication), formId, request));
    }

    @PutMapping("/{formId}/status")
    @Operation(summary = "Activate or deactivate a marketing form")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = MarketingFormResponse.class)))
    public ResponseEntity<?> updateStatus(
            Authentication authentication, @PathVariable Long formId,
            @RequestBody @Valid MarketingFormStatusUpdateRequest request) {
        return response(facade.updateStatus(caller(authentication), formId, request));
    }

    @DeleteMapping("/{formId}")
    @Operation(summary = "Soft-delete an inactive marketing form")
    public ResponseEntity<?> delete(
            Authentication authentication, @PathVariable Long formId,
            @RequestParam Long version) {
        return response(facade.delete(caller(authentication), formId, version));
    }

    @PostMapping("/{formId}/submissions/trends")
    @Operation(summary = "Query successful submission trend for a marketing form")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = MarketingFormSubmissionTrendResponse.class)))
    public ResponseEntity<?> submissionTrend(
            Authentication authentication, @PathVariable Long formId,
            @RequestBody @Valid MarketingFormSubmissionTrendRequest request) {
        return response(facade.submissionTrend(
                caller(authentication), formId, request));
    }

    @PostMapping("/{formId}/submissions/query")
    @Operation(summary = "Query currently visible target-record links for a marketing form")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = MarketingFormSubmissionLinkPageResponse.class)))
    public ResponseEntity<?> querySubmissions(
            Authentication authentication, @PathVariable Long formId,
            @RequestBody @Valid MarketingFormSubmissionQueryRequest request) {
        return response(facade.querySubmissions(
                caller(authentication), formId, request));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
