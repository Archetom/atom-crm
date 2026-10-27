package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OaAnnouncementCreateRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementQueryRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementScheduleRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementUpdateRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementVersionRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.api.dto.response.OaAnnouncementInboxResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementAudienceCandidatePageResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementInboxPageResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementPageResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementReadReceiptResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementResponse;
import io.github.archetom.crm.api.dto.response.OaAnnouncementUnreadCountResponse;
import io.github.archetom.crm.api.facade.OaAnnouncementFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/oa/announcements")
@RequiredArgsConstructor
@Validated
@Tag(name = "OA announcements", description = "Tenant internal plain-text announcements")
public class OaAnnouncementController {

    private final OaAnnouncementFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping
    @Operation(summary = "Query managed OA announcements")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaAnnouncementPageResponse.class)))
    public ResponseEntity<?> query(
            Authentication authentication,
            @ModelAttribute @Valid OaAnnouncementQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @GetMapping("/{announcementId}")
    @Operation(summary = "Get a managed OA announcement")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaAnnouncementResponse.class)))
    public ResponseEntity<?> get(
            Authentication authentication,
            @PathVariable @Positive Long announcementId) {
        return response(facade.get(caller(authentication), announcementId));
    }

    @GetMapping("/audience-candidates")
    @Operation(summary = "Search server-authorized OA announcement audience targets")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaAnnouncementAudienceCandidatePageResponse.class)))
    public ResponseEntity<?> audienceCandidates(
            Authentication authentication,
            @RequestParam @Parameter(schema = @Schema(allowableValues = {
                    "MEMBER", "DEPARTMENT_SUBTREE"})) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size) {
        return response(facade.audienceCandidates(caller(authentication), type, keyword, page, size));
    }

    @PostMapping
    @Operation(summary = "Create a draft OA announcement")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaAnnouncementResponse.class)))
    public ResponseEntity<?> create(
            Authentication authentication,
            @RequestBody @Valid OaAnnouncementCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @PutMapping("/{announcementId}")
    @Operation(summary = "Update a draft OA announcement")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaAnnouncementResponse.class)))
    public ResponseEntity<?> update(
            Authentication authentication,
            @PathVariable @Positive Long announcementId,
            @RequestBody @Valid OaAnnouncementUpdateRequest request) {
        return response(facade.update(
                caller(authentication), announcementId, request));
    }

    @PostMapping("/{announcementId}/publish")
    @Operation(summary = "Publish an OA announcement and snapshot recipients")
    public ResponseEntity<?> publish(
            Authentication authentication,
            @PathVariable @Positive Long announcementId,
            @RequestBody @Valid OaAnnouncementVersionRequest request) {
        return response(facade.publish(
                caller(authentication), announcementId, request));
    }

    @PostMapping("/{announcementId}/schedule")
    @Operation(summary = "Schedule one future OA announcement publication")
    public ResponseEntity<?> schedule(
            Authentication authentication,
            @PathVariable @Positive Long announcementId,
            @RequestBody @Valid OaAnnouncementScheduleRequest request) {
        return response(facade.schedule(caller(authentication), announcementId, request));
    }

    @PostMapping("/{announcementId}/cancel-schedule")
    @Operation(summary = "Cancel one scheduled OA announcement publication")
    public ResponseEntity<?> cancelSchedule(
            Authentication authentication,
            @PathVariable @Positive Long announcementId,
            @RequestBody @Valid OaAnnouncementVersionRequest request) {
        return response(facade.cancelSchedule(caller(authentication), announcementId, request));
    }

    @PostMapping("/{announcementId}/withdraw")
    @Operation(summary = "Withdraw a published OA announcement")
    public ResponseEntity<?> withdraw(
            Authentication authentication,
            @PathVariable @Positive Long announcementId,
            @RequestBody @Valid OaAnnouncementVersionRequest request) {
        return response(facade.withdraw(
                caller(authentication), announcementId, request));
    }

    @DeleteMapping("/{announcementId}")
    @Operation(summary = "Soft-delete a draft or withdrawn OA announcement")
    public ResponseEntity<?> delete(
            Authentication authentication,
            @PathVariable @Positive Long announcementId,
            @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.delete(
                caller(authentication), announcementId, version));
    }

    @GetMapping("/mine")
    @Operation(summary = "Query OA announcements delivered to the caller")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaAnnouncementInboxPageResponse.class)))
    public ResponseEntity<?> mine(
            Authentication authentication,
            @ModelAttribute @Valid QueryRequest request) {
        return response(facade.mine(caller(authentication), request));
    }

    @GetMapping("/mine/{announcementId}")
    @Operation(summary = "Get an OA announcement delivered to the caller")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaAnnouncementInboxResponse.class)))
    public ResponseEntity<?> getMine(
            Authentication authentication,
            @PathVariable @Positive Long announcementId) {
        return response(facade.getMine(caller(authentication), announcementId));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Count currently readable unread OA announcements")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaAnnouncementUnreadCountResponse.class)))
    public ResponseEntity<?> unreadCount(Authentication authentication) {
        return response(facade.unreadCount(caller(authentication)));
    }

    @PostMapping("/mine/{announcementId}/read")
    @Operation(summary = "Idempotently mark a delivered OA announcement read")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OaAnnouncementReadReceiptResponse.class)))
    public ResponseEntity<?> markRead(
            Authentication authentication,
            @PathVariable @Positive Long announcementId) {
        return response(facade.markRead(caller(authentication), announcementId));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
