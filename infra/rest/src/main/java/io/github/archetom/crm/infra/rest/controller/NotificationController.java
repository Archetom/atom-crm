package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.NotificationQueryRequest;
import io.github.archetom.crm.api.dto.request.NotificationPreferenceUpdateRequest;
import io.github.archetom.crm.api.dto.response.NotificationPageResponse;
import io.github.archetom.crm.api.facade.NotificationFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Validated
@Tag(name = "Notifications", description = "Current-member Work reminder inbox")
public class NotificationController {
    private final NotificationFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping
    @Operation(summary = "Query the caller's currently visible notifications", responses = @ApiResponse(
            responseCode = "200", description = "Current-member visible notification inbox page",
            content = @Content(schema = @Schema(implementation = NotificationPageResponse.class))))
    public ResponseEntity<?> query(Authentication authentication,
                                   @ModelAttribute @Valid NotificationQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @PostMapping("/{notificationId}/read")
    @Operation(summary = "Idempotently mark a currently visible notification read")
    public ResponseEntity<?> markRead(Authentication authentication,
                                      @PathVariable @Positive Long notificationId) {
        return response(facade.markRead(caller(authentication), notificationId));
    }

    @GetMapping("/preferences")
    @Operation(summary = "Get the caller's external notification preference")
    public ResponseEntity<?> preferences(Authentication authentication) {
        return response(facade.preferences(caller(authentication)));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update the caller's typed external notification preference")
    public ResponseEntity<?> updatePreferences(Authentication authentication,
                                                @Valid @org.springframework.web.bind.annotation.RequestBody
                                                NotificationPreferenceUpdateRequest request) {
        return response(facade.updatePreferences(caller(authentication), request));
    }

    private AuthenticatedCaller caller(Authentication authentication) { return callerMapper.from(authentication); }
    private ResponseEntity<?> response(Result<?> result) { return ResponseEntityUtil.assembleResponse(result); }
}
