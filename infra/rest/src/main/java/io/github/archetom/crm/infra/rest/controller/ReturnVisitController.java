package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReturnVisitCreateRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitQueryRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitUpdateRequest;
import io.github.archetom.crm.api.dto.response.ReturnVisitPageResponse;
import io.github.archetom.crm.api.dto.response.ReturnVisitResponse;
import io.github.archetom.crm.api.facade.ReturnVisitFacade;
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

@RestController
@RequestMapping("/api/v1/return-visits")
@RequiredArgsConstructor
@Validated
@Tag(name = "Return visits", description = "Tenant-scoped customer return visits")
public class ReturnVisitController {
    private final ReturnVisitFacade facade;
    private final AuthenticatedCallerMapper callerMapper;
    @PostMapping("/query") @Operation(summary = "Query return visits")
    @ApiResponse(responseCode = "200", description = "Return visit page",
            content = @Content(schema = @Schema(implementation = ReturnVisitPageResponse.class)))
    public ResponseEntity<?> query(Authentication authentication, @RequestBody @Valid ReturnVisitQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }
    @GetMapping("/options") @Operation(summary = "List simple return visit options")
    public ResponseEntity<?> options(Authentication authentication,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) Integer limit) {
        return response(facade.options(caller(authentication), keyword, limit));
    }
    @GetMapping("/{returnVisitId}")
    @Operation(summary = "Get a return visit")
    @ApiResponse(responseCode = "200", description = "Return visit detail",
            content = @Content(schema = @Schema(implementation = ReturnVisitResponse.class)))
    public ResponseEntity<?> get(Authentication authentication, @PathVariable @Positive Long returnVisitId) {
        return response(facade.get(caller(authentication), returnVisitId));
    }
    @PostMapping @Operation(summary = "Create a return visit")
    @ApiResponse(responseCode = "200", description = "Created return visit",
            content = @Content(schema = @Schema(implementation = ReturnVisitResponse.class)))
    public ResponseEntity<?> create(Authentication authentication, @RequestBody @Valid ReturnVisitCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }
    @PutMapping("/{returnVisitId}") @Operation(summary = "Update a return visit",
            description = "Omit contactId or contractId to retain its current relation; send an explicit null to clear it. "
                    + "The response labels each optional relation as VISIBLE, HIDDEN, or EMPTY.")
    @ApiResponse(responseCode = "200", description = "Updated return visit detail",
            content = @Content(schema = @Schema(implementation = ReturnVisitResponse.class)))
    public ResponseEntity<?> update(Authentication authentication, @PathVariable @Positive Long returnVisitId,
                                    @RequestBody @Valid ReturnVisitUpdateRequest request) {
        return response(facade.update(caller(authentication), returnVisitId, request));
    }
    @PutMapping("/{returnVisitId}/owner") @Operation(summary = "Change return visit owner")
    @ApiResponse(responseCode = "200", description = "Updated return visit detail",
            content = @Content(schema = @Schema(implementation = ReturnVisitResponse.class)))
    public ResponseEntity<?> updateOwner(Authentication authentication, @PathVariable @Positive Long returnVisitId,
                                         @RequestBody @Valid ReturnVisitOwnerUpdateRequest request) {
        return response(facade.updateOwner(caller(authentication), returnVisitId, request));
    }
    @DeleteMapping("/{returnVisitId}") @Operation(summary = "Soft-delete a return visit")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable @Positive Long returnVisitId,
                                    @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.delete(caller(authentication), returnVisitId, version));
    }
    private AuthenticatedCaller caller(Authentication authentication) { return callerMapper.from(authentication); }
    private ResponseEntity<?> response(Result<?> result) { return ResponseEntityUtil.assembleResponse(result); }
}
