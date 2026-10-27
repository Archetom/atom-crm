package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.service.AttachmentTransferService;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

/** Streams only the exact, visible OA FILE value; the caller never receives a reusable URL. */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/oa/requests/{requestId}/file-fields")
public class OaFileFieldContentController {
    private static final int BUFFER_BYTES = 64 * 1024;
    private final AttachmentTransferService transferService;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping("/{fieldCode}/{fileId}/content")
    public ResponseEntity<StreamingResponseBody> download(Authentication authentication,
            @PathVariable @Positive Long requestId, @PathVariable String fieldCode,
            @PathVariable @Positive Long fileId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        Result<AttachmentTransferService.Download> result = transferService.openOaFileFieldDownload(
                caller(authentication), requestId, fieldCode, fileId, range);
        if (!result.isSuccess()) return failure(result);
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
                    output.write(buffer, 0, count);
                    remaining -= count;
                }
                output.flush();
            } catch (java.io.IOException exception) {
                download.handle().cancel();
                throw exception;
            }
        };
        return new ResponseEntity<>(body, headers,
                download.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResponseEntity<StreamingResponseBody> failure(Result<?> result) {
        return (ResponseEntity) ResponseEntityUtil.assembleResponse(result);
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }
}
