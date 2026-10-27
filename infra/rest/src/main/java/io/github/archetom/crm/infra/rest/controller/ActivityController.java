package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ActivityCreateRequest;
import io.github.archetom.crm.api.dto.request.ActivityQueryRequest;
import io.github.archetom.crm.api.dto.request.ActivityUpdateRequest;
import io.github.archetom.crm.api.dto.request.ActivityOptionRequest;
import io.github.archetom.crm.api.dto.response.ActivityPageResponse;
import io.github.archetom.crm.api.facade.ActivityFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
@Validated
@Tag(name = "Activities", description = "Tenant-scoped business activity timeline")
public class ActivityController {
    private final ActivityFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping("/query")
    @Operation(summary = "Query the activity timeline for one visible record", responses = @ApiResponse(
            responseCode = "200", description = "Server-authorized activity timeline page",
            content = @Content(schema = @Schema(implementation = ActivityPageResponse.class))))
    public ResponseEntity<?> query(Authentication authentication,
                                   @RequestBody @Valid ActivityQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @GetMapping("/options")
    @Operation(summary = "List governed follow-up methods and templates")
    public ResponseEntity<?> options(Authentication authentication) {
        return response(facade.options(caller(authentication)));
    }

    @PutMapping("/options")
    @Operation(summary = "Replace governed follow-up methods and templates")
    public ResponseEntity<?> replaceOptions(
            Authentication authentication,
            @RequestBody List<@Valid ActivityOptionRequest> requests) {
        return response(facade.replaceOptions(caller(authentication), requests));
    }

    @GetMapping("/{activityId}")
    @Operation(summary = "Get one activity")
    public ResponseEntity<?> get(Authentication authentication,
                                 @PathVariable @Positive Long activityId) {
        return response(facade.get(caller(authentication), activityId));
    }

    @PostMapping
    @Operation(summary = "Create a follow-up activity")
    public ResponseEntity<?> create(Authentication authentication,
                                    @RequestBody @Valid ActivityCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @PostMapping("/batch")
    @Operation(summary = "Create up to 100 follow-up activities atomically")
    public ResponseEntity<?> createBatch(Authentication authentication,
                                         @RequestBody List<@Valid ActivityCreateRequest> requests) {
        return response(facade.createBatch(caller(authentication), requests));
    }

    @PutMapping("/{activityId}")
    @Operation(summary = "Update the caller's follow-up activity")
    public ResponseEntity<?> update(Authentication authentication,
                                    @PathVariable @Positive Long activityId,
                                    @RequestBody @Valid ActivityUpdateRequest request) {
        return response(facade.update(caller(authentication), activityId, request));
    }

    @DeleteMapping("/{activityId}")
    @Operation(summary = "Soft-delete the caller's follow-up activity")
    public ResponseEntity<?> delete(Authentication authentication,
                                    @PathVariable @Positive Long activityId,
                                    @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.delete(caller(authentication), activityId, version));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
