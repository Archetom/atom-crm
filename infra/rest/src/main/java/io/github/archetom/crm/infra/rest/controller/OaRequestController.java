package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OaRequestPreviewRequest;
import io.github.archetom.crm.api.dto.request.OaRequestQueryRequest;
import io.github.archetom.crm.api.dto.request.OaRequestSubmitRequest;
import io.github.archetom.crm.api.dto.response.OaRequestPreviewResponse;
import io.github.archetom.crm.api.dto.response.OaRequestResponse;
import io.github.archetom.crm.api.dto.response.OaRequestPageResponse;
import io.github.archetom.crm.api.facade.OaRequestFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/oa/requests")
@RequiredArgsConstructor
@Tag(name = "OA requests", description = "Immutable OA requests backed by B11 approvals")
public class OaRequestController {

    private final OaRequestFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping("/preview")
    @Operation(summary = "Preview an OA request approval route")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaRequestPreviewResponse.class)))
    public ResponseEntity<?> preview(
            Authentication authentication,
            @RequestBody @Valid OaRequestPreviewRequest request) {
        return response(facade.preview(caller(authentication), request));
    }

    @PostMapping
    @Operation(summary = "Atomically submit an OA request and approval")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaRequestResponse.class)))
    public ResponseEntity<?> submit(
            Authentication authentication,
            @RequestBody @Valid OaRequestSubmitRequest request) {
        return response(facade.submit(caller(authentication), request));
    }

    @GetMapping
    @Operation(summary = "Query OA requests visible to the caller")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaRequestPageResponse.class)))
    public ResponseEntity<?> query(
            Authentication authentication,
            @ModelAttribute @Valid OaRequestQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @GetMapping("/{requestId}")
    @Operation(summary = "Get an OA request")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaRequestResponse.class)))
    public ResponseEntity<?> get(
            Authentication authentication, @PathVariable Long requestId) {
        return response(facade.get(caller(authentication), requestId));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
