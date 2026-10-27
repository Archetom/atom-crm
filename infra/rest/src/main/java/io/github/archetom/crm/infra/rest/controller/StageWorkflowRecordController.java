package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.StageAdvanceRequest;
import io.github.archetom.crm.api.dto.request.StageCommentCreateRequest;
import io.github.archetom.crm.api.dto.request.StageOutcomeRequest;
import io.github.archetom.crm.api.dto.response.StageWorkflowActionResult;
import io.github.archetom.crm.api.dto.response.StageWorkflowCommentDeletionResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowCommentPageResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowCommentResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowRecordResponse;
import io.github.archetom.crm.api.dto.response.ApprovalSelectionCandidatePageResponse;
import io.github.archetom.crm.api.dto.response.StageFieldCandidatePageResponse;
import io.github.archetom.crm.api.dto.response.StageFileFieldDraftResponse;
import io.github.archetom.crm.api.facade.StageWorkflowFacade;
import io.github.archetom.crm.infra.rest.result.RestErrorResult;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
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

/**
 * Canonical runtime-record resource.  Its identifier is always a workflow record ID,
 * never the business-record subject ID.
 */
@RestController
@RequestMapping("/api/v1/stage-workflow-records")
@RequiredArgsConstructor
@Tag(name = "Stage workflow records", description = "Runtime stage workflow records and comments")
public class StageWorkflowRecordController {

    private final StageWorkflowFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping("/{workflowRecordId}")
    @Operation(summary = "Get a stage workflow record")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = StageWorkflowRecordResponse.class)))
    public ResponseEntity<?> get(Authentication authentication, @PathVariable Long workflowRecordId) {
        return response(facade.getRecord(caller(authentication), workflowRecordId));
    }

    @GetMapping("/{workflowRecordId}/advance-options/{selectionCode}/candidates")
    @Operation(summary = "List server-validated candidates for the current stage approval selection")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ApprovalSelectionCandidatePageResponse.class)))
    public ResponseEntity<?> advanceSelectionCandidates(
            Authentication authentication, @PathVariable Long workflowRecordId,
            @PathVariable String selectionCode,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size) {
        return response(facade.advanceSelectionCandidates(caller(authentication), workflowRecordId,
                selectionCode, page, size));
    }

    @GetMapping("/{workflowRecordId}/writable-fields/{fieldCode}/candidates")
    @Operation(summary = "List record-scoped candidates for a specialized writable stage field")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = StageFieldCandidatePageResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> fieldCandidates(
            Authentication authentication, @PathVariable Long workflowRecordId,
            @PathVariable String fieldCode, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return response(facade.fieldCandidates(caller(authentication), workflowRecordId,
                fieldCode, keyword, page, size));
    }

    @PostMapping("/{workflowRecordId}/writable-fields/{fieldCode}/file-drafts")
    @Operation(summary = "Create a record-scoped FILE draft for one writable stage field")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = StageFileFieldDraftResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> createStageFileDraft(
            Authentication authentication, @PathVariable Long workflowRecordId,
            @PathVariable String fieldCode) {
        return response(facade.createFileDraft(caller(authentication), workflowRecordId, fieldCode));
    }

    @PutMapping("/{workflowRecordId}/advance")
    @Operation(summary = "Capture allowed stage data and advance a workflow record")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = StageWorkflowActionResult.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> advance(Authentication authentication, @PathVariable Long workflowRecordId,
                                     @RequestBody @Valid StageAdvanceRequest request) {
        return response(facade.advance(caller(authentication), workflowRecordId, request));
    }

    @PutMapping("/{workflowRecordId}/outcome")
    @Operation(summary = "Finish a workflow record with a failed outcome")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = StageWorkflowActionResult.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> outcome(Authentication authentication, @PathVariable Long workflowRecordId,
                                     @RequestBody @Valid StageOutcomeRequest request) {
        return response(facade.outcome(caller(authentication), workflowRecordId, request));
    }

    @GetMapping("/{workflowRecordId}/comments")
    @Operation(summary = "Query stage workflow comments")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = StageWorkflowCommentPageResponse.class)))
    public ResponseEntity<?> comments(Authentication authentication, @PathVariable Long workflowRecordId,
                                      @RequestParam(defaultValue = "1") Integer page,
                                      @RequestParam(defaultValue = "20") Integer size) {
        return response(facade.comments(caller(authentication), workflowRecordId, page, size));
    }

    @PostMapping("/{workflowRecordId}/comments")
    @Operation(summary = "Add a stage workflow comment")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = StageWorkflowCommentResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> comment(Authentication authentication, @PathVariable Long workflowRecordId,
                                     @RequestBody @Valid StageCommentCreateRequest request) {
        return response(facade.comment(caller(authentication), workflowRecordId, request));
    }

    @DeleteMapping("/{workflowRecordId}/comments/{commentId}")
    @Operation(summary = "Delete the caller's stage workflow comment")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = StageWorkflowCommentDeletionResponse.class))),
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
        return response(facade.deleteComment(caller(authentication), workflowRecordId, commentId, version));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
