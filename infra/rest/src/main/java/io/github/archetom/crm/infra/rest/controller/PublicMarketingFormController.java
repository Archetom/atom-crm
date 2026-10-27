package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionRequest;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.response.PublicMarketingFileUploadStatusResponse;
import io.github.archetom.crm.api.dto.response.PublicMarketingFileDraftResponse;
import io.github.archetom.crm.api.dto.response.PublicMarketingFileDraftWithdrawalResponse;
import io.github.archetom.crm.api.dto.response.PublicMarketingFormResponse;
import io.github.archetom.crm.api.dto.response.PublicMarketingUploadIntentResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormSubmissionResponse;
import io.github.archetom.crm.application.security.MarketingFileDraftCredential;
import io.github.archetom.crm.api.facade.MarketingFormFacade;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import java.util.Collections;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/open/marketing-forms")
@RequiredArgsConstructor
@Validated
@Tag(name = "Public marketing forms", description = "Anonymous form rendering and lead submission")
public class PublicMarketingFormController {

    private final MarketingFormFacade facade;

    @GetMapping("/{publicId}")
    @Operation(summary = "Get an active public marketing form")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = PublicMarketingFormResponse.class)))
    public ResponseEntity<?> get(@PathVariable String publicId) {
        return response(facade.getPublic(publicId));
    }

    @PostMapping({"/{publicId}", "/{publicId}/submissions"})
    @Operation(summary = "Submit an active marketing form")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = MarketingFormSubmissionResponse.class))),
            @ApiResponse(responseCode = "400"), @ApiResponse(responseCode = "404"),
            @ApiResponse(responseCode = "409"), @ApiResponse(responseCode = "429")
    })
    public ResponseEntity<?> submit(
            @PathVariable String publicId,
            @Parameter(in = ParameterIn.HEADER,
                    description = "Optional only when the submission binds an anonymous FILE draft; never use Authorization.")
            @RequestHeader(value = MarketingFileDraftCredential.HEADER_NAME, required = false)
            String uploadCapability,
            @RequestBody @Valid MarketingFormSubmissionRequest request) {
        return response(facade.submit(publicId, uploadCapability, request));
    }

    @PostMapping("/{publicId}/file-drafts")
    @Operation(summary = "Create an anonymous MarketingForm FILE draft")
    @ApiResponses({
            @ApiResponse(responseCode = "201", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = PublicMarketingFileDraftResponse.class))),
            @ApiResponse(responseCode = "400"), @ApiResponse(responseCode = "404"),
            @ApiResponse(responseCode = "429"), @ApiResponse(responseCode = "503")
    })
    public ResponseEntity<?> createFileDraft(@PathVariable String publicId) {
        Result<PublicMarketingFileDraftResponse> result = facade.createPublicFileDraft(publicId);
        return result.isSuccess() ? ResponseEntity.status(HttpStatus.CREATED).body(result.getData()) : response(result);
    }

    @PostMapping("/{publicId}/file-drafts/{draftId}/fields/{fieldCode}/upload-intents")
    @Operation(summary = "Create an anonymous MarketingForm FILE upload intent")
    @ApiResponses({
            @ApiResponse(responseCode = "201", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = PublicMarketingUploadIntentResponse.class))),
            @ApiResponse(responseCode = "400"), @ApiResponse(responseCode = "404"),
            @ApiResponse(responseCode = "409"), @ApiResponse(responseCode = "429"),
            @ApiResponse(responseCode = "503")
    })
    public ResponseEntity<?> createFileIntent(@PathVariable String publicId,
                                              @PathVariable @Positive Long draftId,
                                              @PathVariable String fieldCode,
                                              @Parameter(in = ParameterIn.HEADER, required = true,
                                                      description = "One-time public upload capability; never use Authorization.")
                                              @RequestHeader(value = MarketingFileDraftCredential.HEADER_NAME)
                                              String uploadCapability,
                                              @RequestBody @Valid AttachmentUploadIntentCreateRequest request) {
        Result<?> result = facade.createPublicFileIntent(
                publicId, draftId, uploadCapability, fieldCode, request);
        return result.isSuccess() ? ResponseEntity.status(HttpStatus.CREATED).body(result.getData()) : response(result);
    }

    @PutMapping(value = "/{publicId}/file-drafts/{draftId}/fields/{fieldCode}/files/{fileId}/content",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Upload anonymous MarketingForm FILE content",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary"))))
    @ApiResponses({
            @ApiResponse(responseCode = "202", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = PublicMarketingFileUploadStatusResponse.class))),
            @ApiResponse(responseCode = "400"), @ApiResponse(responseCode = "404"),
            @ApiResponse(responseCode = "409"), @ApiResponse(responseCode = "429"),
            @ApiResponse(responseCode = "503")
    })
    public ResponseEntity<?> uploadFile(@PathVariable String publicId,
                                        @PathVariable @Positive Long draftId,
                                        @PathVariable String fieldCode,
                                        @PathVariable @Positive Long fileId,
                                        @Parameter(in = ParameterIn.HEADER, required = true,
                                                description = "One-time public upload capability; never use Authorization.")
                                        @RequestHeader(value = MarketingFileDraftCredential.HEADER_NAME)
                                        String uploadCapability,
                                        @RequestParam @NotNull @Min(0) Long version,
                                        @Parameter(hidden = true)
                                        HttpServletRequest request) throws IOException {
        Long length = contentLength(request);
        if (length == null || request.getHeader(HttpHeaders.CONTENT_ENCODING) != null
                || request.getHeader(HttpHeaders.TRANSFER_ENCODING) != null
                || !org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(request.getContentType())) {
            throw new IllegalArgumentException("attachment upload transport is invalid");
        }
        Result<PublicMarketingFileUploadStatusResponse> result = facade.uploadPublicFile(
                publicId, draftId, uploadCapability, fieldCode, fileId, version,
                request.getInputStream(), length);
        return result.isSuccess() ? ResponseEntity.accepted().body(result.getData()) : response(result);
    }

    @GetMapping("/{publicId}/file-drafts/{draftId}/fields/{fieldCode}/files/{fileId}/upload-status")
    @Operation(summary = "Get an anonymous MarketingForm FILE upload status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = PublicMarketingFileUploadStatusResponse.class))),
            @ApiResponse(responseCode = "400"), @ApiResponse(responseCode = "404"),
            @ApiResponse(responseCode = "429")
    })
    public ResponseEntity<?> uploadStatus(@PathVariable String publicId, @PathVariable @Positive Long draftId,
                                          @PathVariable String fieldCode, @PathVariable @Positive Long fileId,
                                          @Parameter(in = ParameterIn.HEADER, required = true,
                                                  description = "One-time public upload capability; never use Authorization.")
                                          @RequestHeader(value = MarketingFileDraftCredential.HEADER_NAME)
                                          String uploadCapability) {
        return response(facade.publicFileUploadStatus(
                publicId, draftId, uploadCapability, fieldCode, fileId));
    }

    @DeleteMapping("/{publicId}/file-drafts/{draftId}")
    @Operation(summary = "Withdraw an anonymous MarketingForm FILE draft")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = PublicMarketingFileDraftWithdrawalResponse.class))),
            @ApiResponse(responseCode = "400"), @ApiResponse(responseCode = "404"),
            @ApiResponse(responseCode = "429")
    })
    public ResponseEntity<?> withdrawFileDraft(@PathVariable String publicId, @PathVariable @Positive Long draftId,
                                               @Parameter(in = ParameterIn.HEADER, required = true,
                                                       description = "One-time public upload capability; never use Authorization.")
                                               @RequestHeader(value = MarketingFileDraftCredential.HEADER_NAME)
                                               String uploadCapability) {
        Result<PublicMarketingFileDraftWithdrawalResponse> result = facade.withdrawPublicFileDraft(
                publicId, draftId, uploadCapability);
        return result.isSuccess() ? ResponseEntity.ok(result.getData()) : response(result);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
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
