package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.DashboardPreferenceUpdateRequest;
import io.github.archetom.crm.api.dto.response.DashboardPreferenceResponse;
import io.github.archetom.crm.api.facade.DashboardPreferenceFacade;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for current-member dashboard presentation defaults. */
@RestController
@RequestMapping("/api/v1/dashboard/preferences")
@RequiredArgsConstructor
@Tag(name = "Dashboard preferences", description = "Personal dashboard time, scope, and display-module defaults")
public class DashboardPreferenceController {

    private final DashboardPreferenceFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping
    @Operation(summary = "Get the caller's dashboard presentation defaults")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = DashboardPreferenceResponse.class)))
    public ResponseEntity<?> get(Authentication authentication) {
        return response(facade.get(caller(authentication)));
    }

    @PutMapping
    @Operation(summary = "Update the caller's complete dashboard presentation defaults")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = DashboardPreferenceResponse.class)))
    public ResponseEntity<?> update(
            Authentication authentication,
            @RequestBody @Valid DashboardPreferenceUpdateRequest request) {
        return response(facade.update(caller(authentication), request));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
