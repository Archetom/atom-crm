package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
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

/** Streams immutable approval/stage FILE evidence after fresh workflow and field authorization. */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class WorkflowFileFieldContentController {
    private static final int BUFFER_BYTES = 64 * 1024;
    private final AttachmentTransferService transferService;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping("/approval-instances/{instanceId}/file-fields/{fieldCode}/{fileId}/content")
    public ResponseEntity<StreamingResponseBody> approval(Authentication authentication,
            @PathVariable @Positive Long instanceId, @PathVariable String fieldCode,
            @PathVariable @Positive Long fileId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        return response(transferService.openApprovalFileFieldDownload(callerMapper.from(authentication),
                instanceId, fieldCode, fileId, range));
    }

    @GetMapping("/stage-workflow-records/{workflowRecordId}/stages/{stageCode}/file-fields/{fieldCode}/{fileId}/content")
    public ResponseEntity<StreamingResponseBody> stage(Authentication authentication,
            @PathVariable @Positive Long workflowRecordId, @PathVariable String stageCode,
            @PathVariable String fieldCode, @PathVariable @Positive Long fileId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        return response(transferService.openStageFileFieldDownload(callerMapper.from(authentication),
                workflowRecordId, stageCode, fieldCode, fileId, range));
    }

    private ResponseEntity<StreamingResponseBody> response(Result<AttachmentTransferService.Download> result) {
        if (!result.isSuccess()) {
            @SuppressWarnings("unchecked")
            ResponseEntity<StreamingResponseBody> failure = (ResponseEntity<StreamingResponseBody>) (ResponseEntity<?>)
                    ResponseEntityUtil.assembleResponse(result);
            return failure;
        }
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
}
