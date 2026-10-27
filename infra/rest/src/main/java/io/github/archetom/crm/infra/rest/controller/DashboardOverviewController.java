package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.response.DashboardOverviewResponse;
import io.github.archetom.crm.api.facade.DashboardOverviewFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the current member's fixed sales dashboard. */
@RestController
@RequestMapping("/api/v1/dashboard/overview")
@RequiredArgsConstructor
@Tag(name = "Dashboard overview", description = "Fixed permission-aware sales dashboard")
public class DashboardOverviewController {

    private final DashboardOverviewFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping
    @Operation(summary = "Get the caller's fixed sales dashboard")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = DashboardOverviewResponse.class)))
    public ResponseEntity<?> get(Authentication authentication) {
        AuthenticatedCaller caller = callerMapper.from(authentication);
        Result<?> result = facade.get(caller);
        return ResponseEntityUtil.assembleResponse(result);
    }
}
