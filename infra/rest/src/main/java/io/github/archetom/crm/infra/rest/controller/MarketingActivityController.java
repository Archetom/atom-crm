package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.dto.request.MarketingActivityBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityCreateRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityQueryRequest;
import io.github.archetom.crm.api.dto.request.MarketingActivityUpdateRequest;
import io.github.archetom.crm.api.dto.response.MarketingActivityConfigurationOptionsResponse;
import io.github.archetom.crm.api.dto.response.MarketingActivityPageResponse;
import io.github.archetom.crm.api.dto.response.MarketingActivityResponse;
import io.github.archetom.crm.api.facade.MarketingActivityFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/marketing-activities")
@RequiredArgsConstructor
@Tag(name = "Marketing activities", description = "Tenant marketing activity management")
public class MarketingActivityController {
    private final MarketingActivityFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping("/configuration-options")
    @Operation(operationId = "marketingActivityConfigurationOptions",
            summary = "Get server-defined marketing activity configuration options")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = MarketingActivityConfigurationOptionsResponse.class)))
    public ResponseEntity<?> configurationOptions(Authentication authentication) {
        return response(facade.configurationOptions(callerMapper.from(authentication)));
    }

    @GetMapping
    @Operation(summary = "Query marketing activities")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = MarketingActivityPageResponse.class)))
    public ResponseEntity<?> query(
            Authentication authentication, @ModelAttribute @Valid MarketingActivityQueryRequest request) {
        return response(facade.query(callerMapper.from(authentication), request));
    }

    @GetMapping("/{activityId}")
    @Operation(summary = "Get a marketing activity")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = MarketingActivityResponse.class)))
    public ResponseEntity<?> get(Authentication authentication, @PathVariable Long activityId) {
        return response(facade.get(callerMapper.from(authentication), activityId));
    }

    @PostMapping
    @Operation(summary = "Create a marketing activity")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = MarketingActivityResponse.class)))
    public ResponseEntity<?> create(Authentication authentication,
                                    @RequestBody @Valid MarketingActivityCreateRequest request) {
        return response(facade.create(callerMapper.from(authentication), request));
    }

    @PostMapping("/batch-delete")
    @Operation(operationId = "marketingActivityBatchDelete",
            summary = "Atomically delete selected marketing activities")
    public ResponseEntity<?> deleteBatch(Authentication authentication,
                                         @RequestBody @Valid MarketingActivityBatchDeleteRequest request) {
        return response(facade.deleteBatch(callerMapper.from(authentication), request));
    }

    @PutMapping("/{activityId}")
    @Operation(summary = "Update a marketing activity")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = MarketingActivityResponse.class)))
    public ResponseEntity<?> update(Authentication authentication, @PathVariable Long activityId,
                                    @RequestBody @Valid MarketingActivityUpdateRequest request) {
        return response(facade.update(callerMapper.from(authentication), activityId, request));
    }

    @DeleteMapping("/{activityId}")
    @Operation(summary = "Delete a marketing activity")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long activityId,
                                    @RequestParam Long version) {
        return response(facade.delete(callerMapper.from(authentication), activityId, version));
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
