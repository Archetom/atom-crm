package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.dto.request.PasswordLoginOptionsRequest;
import io.github.archetom.crm.api.dto.request.PasswordResetConfirmRequest;
import io.github.archetom.crm.api.dto.request.PasswordResetRequest;
import io.github.archetom.crm.api.dto.request.PasswordSessionCreateRequest;
import io.github.archetom.crm.api.dto.response.AuthenticationSessionResponse;
import io.github.archetom.crm.api.dto.response.PasswordLoginOptionsResponse;
import io.github.archetom.crm.api.context.BrowserSessionIssuance;
import io.github.archetom.crm.api.facade.AuthenticationFacade;
import io.github.archetom.crm.infra.rest.result.RestErrorResult;
import io.github.archetom.crm.infra.rest.security.AuthenticationCookieService;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Public password-login and recovery boundary. Session secrets remain HTTP-only cookies. */
@RestController
@RequestMapping("/api/v1/authentication")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Password login, tenant selection, browser session, and recovery")
public class AuthenticationController {

    private final AuthenticationFacade facade;
    private final AuthenticationCookieService cookieService;

    @Operation(summary = "Verify password login and list selectable tenants")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credentials accepted",
                    content = @Content(schema = @Schema(implementation = PasswordLoginOptionsResponse.class))),
            @ApiResponse(responseCode = "401", description = "Credentials were not accepted",
                    content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    @PostMapping("/password/login-options")
    public ResponseEntity<?> passwordLoginOptions(@Valid @RequestBody PasswordLoginOptionsRequest request) {
        Result<PasswordLoginOptionsResponse> result = facade.beginPasswordLogin(request);
        if (!result.isSuccess()) return ResponseEntityUtil.assembleResponse(result);
        return ResponseEntity.ok(result.getData());
    }

    @Operation(summary = "Create a browser session for one verified tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session cookie established",
                    content = @Content(schema = @Schema(implementation = AuthenticationSessionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Login challenge is invalid or expired",
                    content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    @PostMapping("/password/sessions")
    public ResponseEntity<?> createPasswordSession(
            @Valid @RequestBody PasswordSessionCreateRequest request,
            HttpServletResponse response
    ) {
        Result<BrowserSessionIssuance> result = facade.createBrowserSession(request);
        if (!result.isSuccess()) return ResponseEntityUtil.assembleResponse(result);
        BrowserSessionIssuance issuance = result.getData();
        cookieService.establish(response, issuance.token(),
                Duration.between(java.time.Instant.now(), issuance.expiresAt()));
        return ResponseEntity.ok(issuance.session());
    }

    @Operation(summary = "Read the current browser session")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active browser session",
                    content = @Content(schema = @Schema(implementation = AuthenticationSessionResponse.class))),
            @ApiResponse(responseCode = "401", description = "No active browser session")
    })
    @GetMapping("/session")
    public ResponseEntity<?> currentSession(HttpServletRequest request, HttpServletResponse response) {
        AuthenticationSessionResponse session = cookieService.sessionToken(request)
                .flatMap(facade::resumeBrowserSession)
                .orElse(null);
        if (session == null) {
            cookieService.clear(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // The session cookie the caller presented stays as it is; only the readable CSRF cookie is
        // refreshed, and it expires with the session the presented token already stands for.
        cookieService.ensureCsrfCookie(request, response,
                Duration.between(java.time.Instant.now(), session.getExpiresAt()));
        return ResponseEntity.ok(session);
    }

    @Operation(summary = "End the current browser session")
    @ApiResponse(responseCode = "204", description = "Session cookies cleared")
    @DeleteMapping("/session")
    public ResponseEntity<Void> endSession(HttpServletResponse response) {
        cookieService.clear(response);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Request a password reset message")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Reset request accepted"),
            @ApiResponse(responseCode = "503", description = "Reset delivery is unavailable",
                    content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    @PostMapping("/password/reset-requests")
    public ResponseEntity<?> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        Result<Void> result = facade.requestPasswordReset(request);
        if (!result.isSuccess()) return ResponseEntityUtil.assembleResponse(result);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Set a new password with a one-use reset token")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed"),
            @ApiResponse(responseCode = "401", description = "Reset token is invalid or expired",
                    content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    @PostMapping("/password/reset-confirmations")
    public ResponseEntity<?> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        Result<Void> result = facade.confirmPasswordReset(request);
        if (!result.isSuccess()) return ResponseEntityUtil.assembleResponse(result);
        return ResponseEntity.noContent().build();
    }
}
