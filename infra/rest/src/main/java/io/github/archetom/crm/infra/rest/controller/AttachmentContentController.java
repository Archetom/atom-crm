package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.service.AttachmentTransferService;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** Raw content transport: it validates headers before delegating and never buffers the body. */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/attachment-targets/{targetType}/{targetId}/attachments")
public class AttachmentContentController {
    private static final int BUFFER_BYTES = 64 * 1024;
    private final AttachmentTransferService transferService;
    private final AuthenticatedCallerMapper callerMapper;
    private final ObjectMapper objectMapper;

    @Operation(requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    schema = @Schema(type = "string", format = "binary"))),
            summary = "Upload controlled attachment bytes")
    @ApiResponse(responseCode = "202", description = "Attachment bytes accepted",
            content = @Content(schema = @Schema(type = "string")))
    @PutMapping(value = "/{fileId}/content", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> upload(Authentication authentication, HttpServletRequest request,
            @PathVariable String targetType, @PathVariable @Positive Long targetId,
            @PathVariable @Positive Long fileId, @RequestParam @NotNull @Min(0) Long version) {
        Long length = contentLength(request);
        if (length == null || request.getHeader(HttpHeaders.CONTENT_ENCODING) != null
                || request.getHeader(HttpHeaders.TRANSFER_ENCODING) != null
                || !MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(request.getContentType())) {
            return ResponseEntity.badRequest().body("Invalid attachment upload transport");
        }
        AttachmentByteSource source = new AttachmentByteSource() {
            @Override public java.io.InputStream openStream() throws java.io.IOException { return request.getInputStream(); }
            @Override public long declaredLength() { return length; }
        };
        Result<String> result = transferService.upload(caller(authentication), targetType, targetId, fileId,
                version, source);
        return result.isSuccess() ? ResponseEntity.accepted().body(result.getData())
                : ResponseEntityUtil.assembleResponse(result);
    }

    @GetMapping("/{fileId}/content")
    public ResponseEntity<StreamingResponseBody> download(Authentication authentication, @PathVariable String targetType,
            @PathVariable @Positive Long targetId, @PathVariable @Positive Long fileId,
            @org.springframework.web.bind.annotation.RequestHeader(value = HttpHeaders.RANGE, required = false)
            String range) {
        Result<AttachmentTransferService.Download> result = transferService.openDownload(caller(authentication),
                targetType, targetId, fileId, range);
        if (!result.isSuccess()) return downloadFailure(result);
        AttachmentTransferService.Download download = result.getData();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(download.displayName(),
                StandardCharsets.UTF_8).build());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl("private, no-store");
        headers.setPragma("no-cache");
        headers.set("Accept-Ranges", download.singleRangeReady() ? "bytes" : "none");
        headers.setContentLength(download.length());
        headers.setContentType(MediaType.parseMediaType(download.mediaType()));
        if (download.partial()) headers.set("Content-Range", "bytes " + download.offset() + "-"
                + (download.offset() + download.length() - 1) + "/" + download.totalBytes());
        StreamingResponseBody body = output -> {
            try (var handle = download.handle(); var input = handle.inputStream()) {
                byte[] buffer = new byte[BUFFER_BYTES];
                long remaining = download.length();
                while (remaining > 0) {
                    int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (count < 0) throw new java.io.EOFException("attachment stream ended early");
                    output.write(buffer, 0, count); remaining -= count;
                }
                output.flush();
            } catch (java.io.IOException exception) {
                download.handle().cancel();
                throw exception;
            }
        };
        return new ResponseEntity<>(body, headers, download.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK);
    }

    /** Preserve raw streaming for success while serializing the ordinary error envelope explicitly. */
    private ResponseEntity<StreamingResponseBody> downloadFailure(Result<?> result) {
        ResponseEntity<?> failure = ResponseEntityUtil.assembleResponse(result);
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(failure.getHeaders());
        headers.setContentType(MediaType.APPLICATION_JSON);
        StreamingResponseBody body = output -> objectMapper.writeValue(output, failure.getBody());
        return new ResponseEntity<>(body, headers, failure.getStatusCode());
    }

    private static Long contentLength(HttpServletRequest request) {
        var values = Collections.list(request.getHeaders(HttpHeaders.CONTENT_LENGTH));
        if (values.size() != 1 || !values.getFirst().matches("[0-9]+")) return null;
        try {
            long value = Long.parseLong(values.getFirst());
            return value > 0 && request.getContentLengthLong() == value ? value : null;
        } catch (NumberFormatException exception) { return null; }
    }
    private AuthenticatedCaller caller(Authentication authentication) { return callerMapper.from(authentication); }
}
