package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.StageAdvanceRequest;
import io.github.archetom.crm.api.dto.request.StageCommentCreateRequest;
import io.github.archetom.crm.api.dto.request.StageOutcomeRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowQueryRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowStatusRequest;
import io.github.archetom.crm.api.dto.response.StageWorkflowActionResult;
import io.github.archetom.crm.api.dto.response.StageWorkflowApplicableResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowCommentDeletionResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowCommentPageResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowCommentResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowDetailResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowMutationResult;
import io.github.archetom.crm.api.dto.response.StageWorkflowRecordResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowSummaryPageResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowConfigurationOptionsResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowConfigurationCandidatePageResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowConfigurationCandidateType;
import io.github.archetom.crm.api.facade.StageWorkflowFacade;
import io.github.archetom.crm.infra.rest.result.RestErrorResult;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stage-workflows")
@RequiredArgsConstructor
@Tag(name = "Stage workflows", description = "Versioned business-record stage gates")
public class StageWorkflowController {

    private final StageWorkflowFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping("/query")
    @Operation(summary = "Query stage workflow definitions")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = StageWorkflowSummaryPageResponse.class)))
    public ResponseEntity<?> query(Authentication authentication,
                                   @RequestBody @Valid StageWorkflowQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @GetMapping("/applicable")
    @Operation(summary = "List active workflows applicable to a visible record")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = StageWorkflowApplicableResponse.class)))
    public ResponseEntity<?> applicable(Authentication authentication,
                                        @RequestParam String objectType,
                                        @RequestParam Long recordId) {
        return response(facade.applicable(caller(authentication), objectType, recordId));
    }

    @GetMapping("/configuration-options")
    @Operation(summary = "Get the permission-scoped stage workflow configuration catalog")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = StageWorkflowConfigurationOptionsResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> configurationOptions(Authentication authentication,
                                                  @RequestParam(required = false) String objectType) {
        return response(facade.configurationOptions(caller(authentication), objectType));
    }

    @GetMapping("/configuration-options/candidates")
    @Operation(summary = "List permission-scoped workflow member or department candidates")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = StageWorkflowConfigurationCandidatePageResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> configurationCandidates(Authentication authentication,
                                                     @RequestParam StageWorkflowConfigurationCandidateType candidateType,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "1") Integer page,
                                                     @RequestParam(defaultValue = "20") Integer size) {
        return response(facade.configurationCandidates(caller(authentication), candidateType,
                keyword, page, size));
    }

    @GetMapping("/{workflowId}")
    @Operation(summary = "Get a stage workflow definition")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = StageWorkflowDetailResponse.class)))
    public ResponseEntity<?> get(Authentication authentication, @PathVariable Long workflowId) {
        return response(facade.get(caller(authentication), workflowId));
    }

    @PostMapping
    @Operation(summary = "Create an inactive stage workflow")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = StageWorkflowMutationResult.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> create(Authentication authentication,
                                    @RequestBody @Valid StageWorkflowRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @PutMapping("/{workflowId}")
    @Operation(summary = "Replace an unreferenced stage workflow")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = StageWorkflowMutationResult.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> update(Authentication authentication, @PathVariable Long workflowId,
                                    @RequestBody @Valid StageWorkflowRequest request) {
        return response(facade.update(caller(authentication), workflowId, request));
    }

    @PutMapping("/{workflowId}/status")
    @Operation(summary = "Change stage workflow status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = StageWorkflowMutationResult.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> status(Authentication authentication, @PathVariable Long workflowId,
                                    @RequestBody @Valid StageWorkflowStatusRequest request) {
        return response(facade.changeStatus(caller(authentication), workflowId, request));
    }

    @PostMapping("/{workflowId}/records/{recordId}")
    @Operation(summary = "Start a workflow for a visible business record")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = StageWorkflowRecordResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> start(Authentication authentication, @PathVariable Long workflowId,
                                   @PathVariable Long recordId) {
        return response(facade.start(caller(authentication), workflowId, recordId));
    }

    @GetMapping("/records/{workflowRecordId}")
    @Deprecated
    @Operation(summary = "Get a stage workflow record (deprecated; use /stage-workflow-records/{workflowRecordId})",
            deprecated = true)
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = StageWorkflowRecordResponse.class)))
    public ResponseEntity<?> getRecord(Authentication authentication,
                                       @PathVariable Long workflowRecordId) {
        return response(facade.getRecord(caller(authentication), workflowRecordId));
    }

    @PutMapping("/records/{workflowRecordId}/advance")
    @Deprecated
    @Operation(summary = "Capture stage data and advance or open its approval gate "
            + "(deprecated; use /stage-workflow-records/{workflowRecordId}/advance)", deprecated = true)
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = StageWorkflowActionResult.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> advance(Authentication authentication,
                                     @PathVariable Long workflowRecordId,
                                     @RequestBody @Valid StageAdvanceRequest request) {
        return response(facade.advance(caller(authentication), workflowRecordId, request));
    }

    @PutMapping("/records/{workflowRecordId}/outcome")
    @Deprecated
    @Operation(summary = "Finish a workflow with a failed outcome "
            + "(deprecated; use /stage-workflow-records/{workflowRecordId}/outcome)", deprecated = true)
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = StageWorkflowActionResult.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> outcome(Authentication authentication,
                                     @PathVariable Long workflowRecordId,
                                     @RequestBody @Valid StageOutcomeRequest request) {
        return response(facade.outcome(caller(authentication), workflowRecordId, request));
    }

    @GetMapping("/records/{workflowRecordId}/comments")
    @Deprecated
    @Operation(summary = "Query stage workflow comments "
            + "(deprecated; use /stage-workflow-records/{workflowRecordId}/comments)", deprecated = true)
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = StageWorkflowCommentPageResponse.class)))
    public ResponseEntity<?> comments(Authentication authentication,
                                      @PathVariable Long workflowRecordId,
                                      @RequestParam(defaultValue = "1") Integer page,
                                      @RequestParam(defaultValue = "20") Integer size) {
        return response(facade.comments(caller(authentication), workflowRecordId, page, size));
    }

    @PostMapping("/records/{workflowRecordId}/comments")
    @Deprecated
    @Operation(summary = "Add a stage workflow comment "
            + "(deprecated; use /stage-workflow-records/{workflowRecordId}/comments)", deprecated = true)
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = StageWorkflowCommentResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> comment(Authentication authentication,
                                     @PathVariable Long workflowRecordId,
                                     @RequestBody @Valid StageCommentCreateRequest request) {
        return response(facade.comment(caller(authentication), workflowRecordId, request));
    }

    @DeleteMapping("/records/{workflowRecordId}/comments/{commentId}")
    @Deprecated
    @Operation(summary = "Delete the caller's stage workflow comment "
            + "(deprecated; use /stage-workflow-records/{workflowRecordId}/comments/{commentId})",
            deprecated = true)
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = StageWorkflowCommentDeletionResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> deleteComment(Authentication authentication,
                                           @PathVariable Long workflowRecordId,
                                           @PathVariable Long commentId,
                                           @RequestParam Long version) {
        return response(facade.deleteComment(
                caller(authentication), workflowRecordId, commentId, version));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
