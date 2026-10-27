package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineQueryRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageQueryRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineUpdateRequest;
import io.github.archetom.crm.api.dto.response.OpportunityPipelinePageResponse;
import io.github.archetom.crm.api.dto.response.OpportunityPipelineStagePageResponse;
import io.github.archetom.crm.api.facade.OpportunityPipelineFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/api/v1/opportunity-pipelines")
@RequiredArgsConstructor
@Validated
@Tag(name = "Opportunity pipelines", description = "Tenant sales pipeline and stage catalog")
public class OpportunityPipelineController {

    private final OpportunityPipelineFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping("/query")
    @Operation(summary = "Query tenant opportunity pipelines", responses = @ApiResponse(
            responseCode = "200", description = "Tenant-scoped opportunity pipeline catalog page",
            content = @Content(schema = @Schema(implementation = OpportunityPipelinePageResponse.class))))
    public ResponseEntity<?> query(Authentication authentication,
                                   @RequestBody @Valid OpportunityPipelineQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @GetMapping("/{pipelineId}")
    @Operation(summary = "Get an opportunity pipeline")
    public ResponseEntity<?> get(Authentication authentication,
                                 @PathVariable @Positive Long pipelineId) {
        return response(facade.get(caller(authentication), pipelineId));
    }

    @PostMapping
    @Operation(summary = "Create an inactive opportunity pipeline")
    public ResponseEntity<?> create(Authentication authentication,
                                    @RequestBody @Valid OpportunityPipelineCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @PutMapping("/{pipelineId}")
    @Operation(summary = "Update an opportunity pipeline display configuration")
    public ResponseEntity<?> update(Authentication authentication,
                                    @PathVariable @Positive Long pipelineId,
                                    @RequestBody @Valid OpportunityPipelineUpdateRequest request) {
        return response(facade.update(caller(authentication), pipelineId, request));
    }

    @PutMapping("/{pipelineId}/status")
    @Operation(summary = "Activate or deactivate an opportunity pipeline")
    public ResponseEntity<?> updateStatus(Authentication authentication,
                                          @PathVariable @Positive Long pipelineId,
                                          @RequestBody @Valid OpportunityPipelineStatusUpdateRequest request) {
        return response(facade.changeStatus(caller(authentication), pipelineId, request));
    }

    @DeleteMapping("/{pipelineId}")
    @Operation(summary = "Soft-delete an unused inactive opportunity pipeline")
    public ResponseEntity<?> delete(Authentication authentication,
                                    @PathVariable @Positive Long pipelineId,
                                    @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.delete(caller(authentication), pipelineId, version));
    }

    @PostMapping("/{pipelineId}/stages/query")
    @Operation(summary = "Query tenant stages for one opportunity pipeline", responses = @ApiResponse(
            responseCode = "200", description = "Tenant-scoped opportunity pipeline stage catalog page",
            content = @Content(schema = @Schema(implementation = OpportunityPipelineStagePageResponse.class))))
    public ResponseEntity<?> queryStages(Authentication authentication,
                                         @PathVariable @Positive Long pipelineId,
                                         @RequestBody @Valid OpportunityPipelineStageQueryRequest request) {
        return response(facade.queryStages(caller(authentication), pipelineId, request));
    }

    @GetMapping("/{pipelineId}/stages/{stageId}")
    @Operation(summary = "Get an opportunity pipeline stage")
    public ResponseEntity<?> getStage(Authentication authentication,
                                      @PathVariable @Positive Long pipelineId,
                                      @PathVariable @Positive Long stageId) {
        return response(facade.getStage(caller(authentication), pipelineId, stageId));
    }

    @PostMapping("/{pipelineId}/stages")
    @Operation(summary = "Create an inactive opportunity pipeline stage")
    public ResponseEntity<?> createStage(Authentication authentication,
                                         @PathVariable @Positive Long pipelineId,
                                         @RequestBody @Valid OpportunityPipelineStageCreateRequest request) {
        return response(facade.createStage(caller(authentication), pipelineId, request));
    }

    @PutMapping("/{pipelineId}/stages/{stageId}")
    @Operation(summary = "Update an opportunity pipeline stage display configuration")
    public ResponseEntity<?> updateStage(Authentication authentication,
                                         @PathVariable @Positive Long pipelineId,
                                         @PathVariable @Positive Long stageId,
                                         @RequestBody @Valid OpportunityPipelineStageUpdateRequest request) {
        return response(facade.updateStage(caller(authentication), pipelineId, stageId, request));
    }

    @PutMapping("/{pipelineId}/stages/{stageId}/status")
    @Operation(summary = "Activate or deactivate an opportunity pipeline stage")
    public ResponseEntity<?> updateStageStatus(Authentication authentication,
                                               @PathVariable @Positive Long pipelineId,
                                               @PathVariable @Positive Long stageId,
                                               @RequestBody @Valid OpportunityPipelineStageStatusUpdateRequest request) {
        return response(facade.changeStageStatus(
                caller(authentication), pipelineId, stageId, request));
    }

    @DeleteMapping("/{pipelineId}/stages/{stageId}")
    @Operation(summary = "Soft-delete an inactive opportunity pipeline stage")
    public ResponseEntity<?> deleteStage(Authentication authentication,
                                         @PathVariable @Positive Long pipelineId,
                                         @PathVariable @Positive Long stageId,
                                         @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.deleteStage(caller(authentication), pipelineId, stageId, version));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
