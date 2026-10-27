package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.response.AttachmentUploadIntentResponse;
import io.github.archetom.crm.api.facade.FileFieldDraftFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/attachment-targets/{targetType}/{targetId}/file-fields")
public class FileFieldCandidateController {
    private final FileFieldDraftFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping("/{fieldCode}/upload-intents")
    @Operation(summary = "Create a controlled FILE field upload intent for an existing record")
    @ApiResponses({
            @ApiResponse(responseCode = "201", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AttachmentUploadIntentResponse.class))),
            @ApiResponse(responseCode = "400"),
            @ApiResponse(responseCode = "403"),
            @ApiResponse(responseCode = "404"),
            @ApiResponse(responseCode = "409"),
            @ApiResponse(responseCode = "422")
    })
    public ResponseEntity<?> intent(Authentication authentication, @PathVariable String targetType,
                                    @PathVariable @Positive Long targetId, @PathVariable String fieldCode,
                                    @RequestBody @Valid AttachmentUploadIntentCreateRequest request) {
        Result<?> result = facade.createDirectIntent(callerMapper.from(authentication), targetType, targetId,
                fieldCode, request);
        return result.isSuccess() ? ResponseEntity.status(HttpStatus.CREATED).body(result.getData())
                : ResponseEntityUtil.assembleResponse(result);
    }
}
