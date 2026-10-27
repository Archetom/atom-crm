package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionQueryRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionStatusRequest;
import io.github.archetom.crm.api.dto.request.ApprovalPreviewRequest;
import io.github.archetom.crm.api.dto.response.ApprovalDefinitionPageResponse;
import io.github.archetom.crm.api.dto.response.ApprovalDefinitionResponse;
import io.github.archetom.crm.api.dto.response.ApprovalFieldResponse;
import io.github.archetom.crm.api.dto.response.ApprovalPreviewResponse;
import io.github.archetom.crm.api.facade.ApprovalFacade;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/approval-definitions")
@RequiredArgsConstructor
@Tag(name = "Approval definitions", description = "Tenant approval routing configuration")
public class ApprovalDefinitionController {

    private final ApprovalFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping("/fields")
    @Operation(summary = "List typed approval routing fields")
    @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(
            schema = @Schema(implementation = ApprovalFieldResponse.class))))
    public ResponseEntity<?> fields(Authentication authentication,
                                    @RequestParam String objectType) {
        return response(facade.fields(caller(authentication), objectType));
    }

    @PostMapping("/query")
    @Operation(summary = "Query approval definitions")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ApprovalDefinitionPageResponse.class)))
    public ResponseEntity<?> query(Authentication authentication,
                                   @RequestBody @Valid ApprovalDefinitionQueryRequest request) {
        return response(facade.queryDefinitions(caller(authentication), request));
    }

    @GetMapping("/{definitionId}")
    @Operation(summary = "Get an approval definition")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ApprovalDefinitionResponse.class)))
    public ResponseEntity<?> get(Authentication authentication,
                                 @PathVariable Long definitionId) {
        return response(facade.getDefinition(caller(authentication), definitionId));
    }

    @PostMapping
    @Operation(summary = "Create an inactive approval definition")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = ApprovalDefinitionResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> create(Authentication authentication,
                                    @RequestBody @Valid ApprovalDefinitionRequest request) {
        return response(facade.createDefinition(caller(authentication), request));
    }

    @PutMapping("/{definitionId}")
    @Operation(summary = "Replace an approval definition")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = ApprovalDefinitionResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> update(Authentication authentication,
                                    @PathVariable Long definitionId,
                                    @RequestBody @Valid ApprovalDefinitionRequest request) {
        return response(facade.updateDefinition(caller(authentication), definitionId, request));
    }

    @PutMapping("/{definitionId}/status")
    @Operation(summary = "Change approval definition status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = ApprovalDefinitionResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> status(Authentication authentication,
                                    @PathVariable Long definitionId,
                                    @RequestBody @Valid ApprovalDefinitionStatusRequest request) {
        return response(facade.changeDefinitionStatus(
                caller(authentication), definitionId, request));
    }

    @PostMapping("/{definitionId}/preview")
    @Operation(summary = "Preview the resolved approval route")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApprovalPreviewResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> preview(Authentication authentication,
                                     @PathVariable Long definitionId,
                                     @RequestBody @Valid ApprovalPreviewRequest request) {
        return response(facade.preview(caller(authentication), definitionId, request));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
