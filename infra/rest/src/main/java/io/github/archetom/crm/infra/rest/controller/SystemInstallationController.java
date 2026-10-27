package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.dto.request.InitialTenantAdminRequest;
import io.github.archetom.crm.api.dto.response.AuthenticationSessionResponse;
import io.github.archetom.crm.api.dto.response.InitialInstallationStatusResponse;
import io.github.archetom.crm.api.context.BrowserSessionIssuance;
import io.github.archetom.crm.api.facade.SystemInstallationFacade;
import io.github.archetom.crm.infra.rest.result.RestErrorResult;
import io.github.archetom.crm.infra.rest.security.AuthenticationCookieService;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/** Public one-time setup boundary for a fresh local deployment. */
@RestController
@RequestMapping("/api/v1/installation")
@RequiredArgsConstructor
@Tag(name = "Installation", description = "One-time local tenant administrator installation")
public class SystemInstallationController {

    private final SystemInstallationFacade facade;
    private final AuthenticationCookieService cookieService;

    @Operation(summary = "Read whether first-time installation remains available")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = InitialInstallationStatusResponse.class)))
    @GetMapping
    public ResponseEntity<?> installationStatus() {
        Result<InitialInstallationStatusResponse> result = facade.installationStatus();
        if (!result.isSuccess()) return ResponseEntityUtil.assembleResponse(result);
        return ResponseEntity.ok(result.getData());
    }

    @Operation(summary = "Create the initial tenant owner and establish a browser session")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Initial owner created and session cookie established",
                    content = @Content(schema = @Schema(implementation = AuthenticationSessionResponse.class))),
            @ApiResponse(responseCode = "409", description = "Installation has already completed",
                    content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    @PostMapping("/tenant-admin")
    public ResponseEntity<?> initializeTenantAdmin(
            @Valid @RequestBody InitialTenantAdminRequest request,
            HttpServletResponse response
    ) {
        Result<BrowserSessionIssuance> result = facade.initializeTenantAdmin(request);
        if (!result.isSuccess()) return ResponseEntityUtil.assembleResponse(result);
        BrowserSessionIssuance issuance = result.getData();
        cookieService.establish(response, issuance.token(),
                Duration.between(Instant.now(), issuance.expiresAt()));
        return ResponseEntity.ok(issuance.session());
    }
}
