package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.response.AttachmentPageResponse;
import io.github.archetom.crm.api.dto.response.AttachmentResponse;
import io.github.archetom.crm.api.dto.response.AttachmentUploadIntentResponse;
import io.github.archetom.crm.api.dto.response.AttachmentUploadStatusResponse;
import io.github.archetom.crm.api.facade.AttachmentFacade;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/attachment-targets/{targetType}/{targetId}/attachments")
@Tag(name = "Controlled attachments", description = "Tenant-scoped attachment lifecycle")
public class AttachmentController {
    private final AttachmentFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping("/upload-intents")
    @Operation(summary = "Create a controlled attachment upload intent")
    @ApiResponse(responseCode = "201", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = AttachmentUploadIntentResponse.class)))
    public ResponseEntity<?> createIntent(Authentication authentication, @PathVariable String targetType,
            @PathVariable @Positive Long targetId,
            @RequestBody @Valid AttachmentUploadIntentCreateRequest request) {
        Result<?> result = facade.createIntent(caller(authentication), targetType, targetId, request);
        if (result.isSuccess()) return ResponseEntity.status(HttpStatus.CREATED).body(result.getData());
        return ResponseEntityUtil.assembleResponse(result);
    }

    @GetMapping("/{fileId}/upload-status")
    @Operation(summary = "Get controlled attachment upload status")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = AttachmentUploadStatusResponse.class)))
    public ResponseEntity<?> uploadStatus(Authentication authentication, @PathVariable String targetType,
            @PathVariable @Positive Long targetId, @PathVariable @Positive Long fileId) {
        return response(facade.uploadStatus(caller(authentication), targetType, targetId, fileId));
    }

    @GetMapping
    @Operation(summary = "Query controlled attachments")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = AttachmentPageResponse.class)))
    public ResponseEntity<?> query(Authentication authentication, @PathVariable String targetType,
            @PathVariable @Positive Long targetId, @RequestParam(defaultValue = "1") @Positive Integer page,
            @RequestParam(defaultValue = "20") @Positive Integer size,
            @Parameter(schema = @Schema(allowableValues = {"COVER", "DETAIL"},
                    description = "Only filters marketing-activity image attachment groups"))
            @RequestParam(required = false) String marketingActivityImageType,
            @Parameter(schema = @Schema(allowableValues = {"PRIMARY", "DETAIL"},
                    description = "Only filters Product image attachment groups"))
            @RequestParam(required = false) String productImageType) {
        return response(facade.query(caller(authentication), targetType, targetId, page, size,
                marketingActivityImageType, productImageType));
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "Get a controlled attachment")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = AttachmentResponse.class)))
    public ResponseEntity<?> get(Authentication authentication, @PathVariable String targetType,
            @PathVariable @Positive Long targetId, @PathVariable @Positive Long fileId) {
        return response(facade.get(caller(authentication), targetType, targetId, fileId));
    }

    @DeleteMapping("/{fileId}")
    @Operation(summary = "Withdraw a controlled attachment")
    @ApiResponse(responseCode = "204", description = "Attachment withdrawn")
    public ResponseEntity<?> withdraw(Authentication authentication, @PathVariable String targetType,
            @PathVariable @Positive Long targetId, @PathVariable @Positive Long fileId,
            @RequestParam @NotNull @Min(0) Long version) {
        Result<?> result = facade.withdraw(caller(authentication), targetType, targetId, fileId, version);
        return result.isSuccess() ? ResponseEntity.noContent().build() : ResponseEntityUtil.assembleResponse(result);
    }

    private AuthenticatedCaller caller(Authentication authentication) { return callerMapper.from(authentication); }
    private ResponseEntity<?> response(Result<?> result) { return ResponseEntityUtil.assembleResponse(result); }
}
