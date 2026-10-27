package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.NavigationPreferenceUpdateRequest;
import io.github.archetom.crm.api.dto.response.NavigationPreferenceResponse;
import io.github.archetom.crm.api.facade.NavigationFacade;
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

/** HTTP adapter for current-caller navigation and bounded preferences. */
@RestController
@RequestMapping("/api/v1/navigation")
@RequiredArgsConstructor
@Tag(name = "Navigation", description = "Code-defined navigation visibility")
public class NavigationController {

    private final NavigationFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping
    @Operation(summary = "Get the caller's visible navigation tree")
    public ResponseEntity<?> getNavigation(Authentication authentication) {
        return response(facade.getNavigation(caller(authentication)));
    }

    @GetMapping("/catalog")
    @Operation(summary = "Get the complete code-defined navigation catalog")
    public ResponseEntity<?> getCatalog(Authentication authentication) {
        return response(facade.getCatalog(caller(authentication)));
    }

    @GetMapping("/preferences")
    @Operation(summary = "Get the caller's navigation preference")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = NavigationPreferenceResponse.class)))
    public ResponseEntity<?> getPreference(Authentication authentication) {
        return response(facade.getPreference(caller(authentication)));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update the caller's bounded navigation preference")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = NavigationPreferenceResponse.class)))
    public ResponseEntity<?> updatePreference(Authentication authentication,
                                              @RequestBody @Valid NavigationPreferenceUpdateRequest request) {
        return response(facade.updatePreference(caller(authentication), request));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
