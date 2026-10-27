package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.response.ProductImageDraftResponse;
import io.github.archetom.crm.api.dto.response.ProductImageUploadIntentResponse;
import io.github.archetom.crm.api.dto.response.ProductImageUploadStatusResponse;
import io.github.archetom.crm.api.facade.FileFieldUploadContent;
import io.github.archetom.crm.api.facade.ProductImageDraftFacade;
import io.github.archetom.crm.infra.rest.result.RestErrorResult;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

import java.util.Collections;

/** Authenticated pre-create Product primary/detail image upload flow. */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/product-image-drafts")
public class ProductImageDraftController {
    private final ProductImageDraftFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping
    @Operation(summary = "Create a private Product image draft")
    @ApiResponses({@ApiResponse(responseCode = "201", content = @Content(
            schema = @Schema(implementation = ProductImageDraftResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))})
    public ResponseEntity<?> create(Authentication authentication) {
        Result<ProductImageDraftResponse> result = facade.create(callerMapper.from(authentication));
        return result.isSuccess() ? ResponseEntity.status(HttpStatus.CREATED).body(result.getData())
                : ResponseEntityUtil.assembleResponse(result);
    }

    @PostMapping("/{draftId}/images/{imageType}/upload-intents")
    @Operation(summary = "Create one staged Product image upload instruction")
    @ApiResponse(responseCode = "201", content = @Content(
            schema = @Schema(implementation = ProductImageUploadIntentResponse.class)))
    public ResponseEntity<?> createIntent(Authentication authentication, @PathVariable @Positive Long draftId,
            @PathVariable String imageType, @RequestBody @Valid AttachmentUploadIntentCreateRequest request) {
        Result<ProductImageUploadIntentResponse> result = facade.createIntent(callerMapper.from(authentication),
                draftId, imageType, request);
        return result.isSuccess() ? ResponseEntity.status(HttpStatus.CREATED).body(result.getData())
                : ResponseEntityUtil.assembleResponse(result);
    }

    @PutMapping(value = "/{draftId}/images/{imageType}/files/{fileId}/content",
            consumes = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Upload bytes for one staged Product image")
    @ApiResponse(responseCode = "202", content = @Content(
            schema = @Schema(implementation = ProductImageUploadStatusResponse.class)))
    public ResponseEntity<?> upload(Authentication authentication, HttpServletRequest request,
            @PathVariable @Positive Long draftId, @PathVariable String imageType,
            @PathVariable @Positive Long fileId, @RequestParam @NotNull @Min(0) Long version) {
        Long length = contentLength(request);
        if (length == null || request.getHeader(HttpHeaders.CONTENT_ENCODING) != null
                || request.getHeader(HttpHeaders.TRANSFER_ENCODING) != null
                || !org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(request.getContentType())) {
            return ResponseEntity.badRequest().body("Invalid attachment upload transport");
        }
        FileFieldUploadContent content = new FileFieldUploadContent() {
            @Override public java.io.InputStream openStream() throws java.io.IOException { return request.getInputStream(); }
            @Override public long declaredLength() { return length; }
        };
        Result<ProductImageUploadStatusResponse> result = facade.upload(callerMapper.from(authentication), draftId,
                imageType, fileId, version, content);
        return result.isSuccess() ? ResponseEntity.accepted().body(result.getData())
                : ResponseEntityUtil.assembleResponse(result);
    }

    @GetMapping("/{draftId}/images/{imageType}/files/{fileId}/upload-status")
    @Operation(summary = "Get one staged Product image upload status")
    public ResponseEntity<?> uploadStatus(Authentication authentication, @PathVariable @Positive Long draftId,
            @PathVariable String imageType, @PathVariable @Positive Long fileId) {
        return ResponseEntityUtil.assembleResponse(facade.uploadStatus(callerMapper.from(authentication), draftId,
                imageType, fileId));
    }

    @DeleteMapping("/{draftId}")
    @Operation(summary = "Withdraw a private Product image draft")
    public ResponseEntity<?> withdraw(Authentication authentication, @PathVariable @Positive Long draftId,
            @RequestParam @NotNull @Min(0) Long version) {
        Result<Void> result = facade.withdraw(callerMapper.from(authentication), draftId, version);
        return result.isSuccess() ? ResponseEntity.noContent().build() : ResponseEntityUtil.assembleResponse(result);
    }

    private static Long contentLength(HttpServletRequest request) {
        var values = Collections.list(request.getHeaders(HttpHeaders.CONTENT_LENGTH));
        if (values.size() != 1 || !values.getFirst().matches("[0-9]+")) return null;
        try {
            long value = Long.parseLong(values.getFirst());
            return value > 0 && request.getContentLengthLong() == value ? value : null;
        } catch (NumberFormatException exception) { return null; }
    }
}
