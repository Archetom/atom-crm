package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ApprovalActionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalBatchActionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalQueryRequest;
import io.github.archetom.crm.api.dto.request.ApprovalSubmitRequest;
import io.github.archetom.crm.api.dto.response.ApprovalActionResult;
import io.github.archetom.crm.api.dto.response.ApprovalBatchActionResult;
import io.github.archetom.crm.api.dto.response.ApprovalInstanceResponse;
import io.github.archetom.crm.api.dto.response.ApprovalInstanceSummaryPageResponse;
import io.github.archetom.crm.api.dto.response.ApprovalPendingCountResponse;
import io.github.archetom.crm.api.dto.response.ApprovalStartOptionsResponse;
import io.github.archetom.crm.api.dto.response.ApprovalSelectionCandidatePageResponse;
import io.github.archetom.crm.api.dto.response.ApprovalTransferCandidatePageResponse;
import io.github.archetom.crm.api.facade.ApprovalFacade;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
@Tag(name = "Approvals", description = "Approval submissions, work queues, and actions")
public class ApprovalController {

    private final ApprovalFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping
    @Operation(summary = "Submit an approval")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApprovalInstanceResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> submit(Authentication authentication,
                                    @RequestBody @Valid ApprovalSubmitRequest request) {
        return response(facade.submit(caller(authentication), request));
    }

    @GetMapping("/{instanceId}")
    @Operation(summary = "Get an approval and its task timeline")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApprovalInstanceResponse.class)))
    public ResponseEntity<?> get(Authentication authentication, @PathVariable Long instanceId) {
        return response(facade.getInstance(caller(authentication), instanceId));
    }

    @PostMapping("/query")
    @Operation(summary = "Query approval work buckets")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ApprovalInstanceSummaryPageResponse.class)))
    public ResponseEntity<?> query(Authentication authentication,
                                   @RequestBody @Valid ApprovalQueryRequest request) {
        return response(facade.queryInstances(caller(authentication), request));
    }

    @GetMapping("/pending-count")
    @Operation(summary = "Count caller pending approvals")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApprovalPendingCountResponse.class)))
    public ResponseEntity<?> pendingCount(Authentication authentication) {
        return response(facade.pendingCount(caller(authentication)));
    }

    @GetMapping("/subjects/{objectType}/{subjectId}/start-options")
    @Operation(summary = "List approval definitions the caller may start for this record")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApprovalStartOptionsResponse.class)))
    public ResponseEntity<?> startOptions(Authentication authentication,
                                          @PathVariable String objectType,
                                          @PathVariable Long subjectId) {
        return response(facade.startOptions(caller(authentication), objectType, subjectId));
    }

    @GetMapping("/subjects/{objectType}/{subjectId}/start-options/{definitionId}/selections/{selectionCode}/candidates")
    @Operation(summary = "List server-validated candidates for one approval start selection")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ApprovalSelectionCandidatePageResponse.class)))
    public ResponseEntity<?> startSelectionCandidates(
            Authentication authentication, @PathVariable String objectType,
            @PathVariable Long subjectId, @PathVariable Long definitionId,
            @PathVariable String selectionCode,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size) {
        return response(facade.startSelectionCandidates(caller(authentication), objectType,
                subjectId, definitionId, selectionCode, page, size));
    }

    @GetMapping("/{instanceId}/transfer-candidates")
    @Operation(summary = "List server-validated candidates for the caller's transfer action")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = ApprovalTransferCandidatePageResponse.class)))
    public ResponseEntity<?> transferCandidates(Authentication authentication,
                                                @PathVariable Long instanceId,
                                                @RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "50") Integer size) {
        return response(facade.transferCandidates(caller(authentication), instanceId, page, size));
    }

    @PostMapping("/{instanceId}/actions")
    @Operation(summary = "Approve, reject, or transfer a pending task")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApprovalActionResult.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> act(Authentication authentication, @PathVariable Long instanceId,
                                 @RequestBody @Valid ApprovalActionRequest request) {
        return response(facade.act(caller(authentication), instanceId, request));
    }

    @PostMapping("/actions/batch")
    @Operation(summary = "Atomically act on up to 50 approvals")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApprovalBatchActionResult.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> batchAct(Authentication authentication,
                                      @RequestBody @Valid ApprovalBatchActionRequest request) {
        return response(facade.batchAct(caller(authentication), request));
    }

    @PostMapping("/{instanceId}/withdraw")
    @Operation(summary = "Withdraw an unacted approval")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApprovalActionResult.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> withdraw(Authentication authentication,
                                      @PathVariable Long instanceId,
                                      @RequestParam Long version) {
        return response(facade.withdraw(caller(authentication), instanceId, version));
    }

    @PostMapping("/{instanceId}/cancel")
    @Operation(summary = "Cancel a pending approval as its manager")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApprovalActionResult.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> cancel(Authentication authentication,
                                    @PathVariable Long instanceId,
                                    @RequestParam Long version) {
        return response(facade.cancel(caller(authentication), instanceId, version));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
