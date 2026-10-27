package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.request.FileFieldDraftCreateRequest;
import io.github.archetom.crm.api.dto.response.FileFieldDraftResponse;
import io.github.archetom.crm.api.dto.response.FileFieldUploadIntentResponse;
import io.github.archetom.crm.api.dto.response.FileFieldUploadedFileResponse;
import io.github.archetom.crm.api.facade.FileFieldDraftFacade;
import io.github.archetom.crm.api.facade.FileFieldUploadContent;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
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
import jakarta.servlet.http.HttpServletRequest;
import io.github.archetom.crm.infra.rest.result.RestErrorResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.Collections;

/** Targetless only while the one-shot draft is still private; ordinary attachment routes never list it. */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/file-field-drafts")
public class FileFieldDraftController {
    private final FileFieldDraftFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping
    @Operation(summary = "Create a named one-shot FILE draft")
    @ApiResponses({
            @ApiResponse(responseCode = "201", content = @Content(
                    schema = @Schema(implementation = FileFieldDraftResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> create(Authentication authentication,
                                    @RequestBody @Valid FileFieldDraftCreateRequest request) {
        Result<?> result = facade.createCrmDraft(callerMapper.from(authentication), request);
        return result.isSuccess() ? ResponseEntity.status(HttpStatus.CREATED).body(result.getData())
                : ResponseEntityUtil.assembleResponse(result);
    }
    @PostMapping("/{draftId}/fields/{fieldCode}/upload-intents")
    @Operation(summary = "Create a named FILE upload instruction for a staged field")
    @ApiResponses({
            @ApiResponse(responseCode = "201", content = @Content(
                    schema = @Schema(implementation = FileFieldUploadIntentResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> intent(Authentication authentication, @PathVariable @Positive Long draftId,
                                    @PathVariable String fieldCode,
                                    @RequestBody @Valid AttachmentUploadIntentCreateRequest request) {
        Result<?> result = facade.createDraftIntent(callerMapper.from(authentication), draftId, fieldCode, request);
        return result.isSuccess() ? ResponseEntity.status(HttpStatus.CREATED).body(result.getData())
                : ResponseEntityUtil.assembleResponse(result);
    }
    @PutMapping(value = "/{draftId}/fields/{fieldCode}/files/{fileId}/content",
            consumes = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Upload FILE bytes for one staged field candidate")
    @ApiResponses({
            @ApiResponse(responseCode = "202", content = @Content(
                    schema = @Schema(implementation = FileFieldUploadedFileResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> upload(Authentication authentication, HttpServletRequest request,
                                    @PathVariable @Positive Long draftId, @PathVariable String fieldCode,
                                    @PathVariable @Positive Long fileId,
                                    @RequestParam @NotNull @Min(0) Long version) {
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
        Result<FileFieldUploadedFileResponse> result = facade.upload(callerMapper.from(authentication),
                draftId, fieldCode, fileId, version, content);
        return result.isSuccess() ? ResponseEntity.accepted().body(result.getData())
                : ResponseEntityUtil.assembleResponse(result);
    }
    @GetMapping("/{draftId}/fields/{fieldCode}/files/{fileId}/upload-status")
    @Operation(summary = "Get named staged FILE upload status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = FileFieldUploadedFileResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> uploadStatus(Authentication authentication, @PathVariable @Positive Long draftId,
                                          @PathVariable String fieldCode, @PathVariable @Positive Long fileId) {
        return ResponseEntityUtil.assembleResponse(facade.uploadStatus(callerMapper.from(authentication),
                draftId, fieldCode, fileId));
    }
    @DeleteMapping("/{draftId}")
    @Operation(summary = "Withdraw one FILE draft")
    @ApiResponses({
            @ApiResponse(responseCode = "204"),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> withdraw(Authentication authentication, @PathVariable @Positive Long draftId,
                                      @RequestParam @NotNull @Min(0) Long version) {
        Result<?> result = facade.withdraw(callerMapper.from(authentication), draftId, version);
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
