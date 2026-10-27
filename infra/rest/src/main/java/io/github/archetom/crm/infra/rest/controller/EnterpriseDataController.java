package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.EnterpriseIdentifierLookupRequest;
import io.github.archetom.crm.api.dto.request.EnterpriseNameSearchRequest;
import io.github.archetom.crm.api.dto.request.EnterpriseDataAdoptionPreviewRequest;
import io.github.archetom.crm.api.dto.request.EnterpriseDataAdoptRequest;
import io.github.archetom.crm.api.dto.response.EnterpriseDataAdoptionPreviewResponse;
import io.github.archetom.crm.api.dto.response.EnterpriseDataAdoptionResponse;
import io.github.archetom.crm.api.dto.response.EnterpriseDataCapabilityResponse;
import io.github.archetom.crm.api.dto.response.EnterpriseDataObservationResponse;
import io.github.archetom.crm.api.facade.EnterpriseDataFacade;
import io.github.archetom.crm.infra.rest.result.RestErrorResult;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for provider-neutral, human-triggered enterprise-data reads. */
@RestController
@RequestMapping("/api/v1/enterprise-data")
@RequiredArgsConstructor
@Tag(name = "Enterprise Data", description = "Governed legal-entity lookup")
public class EnterpriseDataController {

    private final EnterpriseDataFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping("/capabilities")
    @Operation(summary = "Get the current tenant enterprise-data capability")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = EnterpriseDataCapabilityResponse.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> capabilities(Authentication authentication) {
        return response(facade.capabilities(caller(authentication)));
    }

    @PostMapping("/search")
    @Operation(summary = "Search one bounded page by registered name")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = EnterpriseDataObservationResponse.class)))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "429", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "503", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> search(
            Authentication authentication,
            @RequestBody @Valid EnterpriseNameSearchRequest request) {
        return response(facade.search(caller(authentication), request));
    }

    @PostMapping("/lookup")
    @Operation(summary = "Look up one entity by an exact registered identifier")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = EnterpriseDataObservationResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "429", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "503", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> lookup(
            Authentication authentication,
            @RequestBody @Valid EnterpriseIdentifierLookupRequest request) {
        return response(facade.lookup(caller(authentication), request));
    }

    @PostMapping("/adoption-preview")
    @Operation(summary = "Create a short-lived exact-lookup adoption preview for one Customer")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = EnterpriseDataAdoptionPreviewResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "429", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "503", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> previewAdoption(Authentication authentication,
            @RequestBody @Valid EnterpriseDataAdoptionPreviewRequest request) {
        return response(facade.previewAdoption(caller(authentication), request));
    }

    @PostMapping("/adopt")
    @Operation(summary = "Adopt explicitly selected enterprise-data facts into one Customer")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = EnterpriseDataAdoptionResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "429", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "503", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> adopt(Authentication authentication,
            @RequestBody @Valid EnterpriseDataAdoptRequest request) {
        return response(facade.adopt(caller(authentication), request));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
