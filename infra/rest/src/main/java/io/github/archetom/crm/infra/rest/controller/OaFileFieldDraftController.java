package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.dto.request.OaFileFieldDraftCreateRequest;
import io.github.archetom.crm.api.dto.response.FileFieldDraftResponse;
import io.github.archetom.crm.api.facade.FileFieldDraftFacade;
import io.github.archetom.crm.infra.rest.result.RestErrorResult;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/** Server-pins an OA FILE draft to a locked active form revision. */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/oa/forms/{formId}/file-field-drafts")
public class OaFileFieldDraftController {
    private final FileFieldDraftFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping
    @Operation(summary = "Create a named OA form FILE draft")
    @ApiResponses({
            @ApiResponse(responseCode = "201", content = @Content(
                    schema = @Schema(implementation = FileFieldDraftResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> create(Authentication authentication, @PathVariable @Positive Long formId,
                                    @RequestBody @Valid OaFileFieldDraftCreateRequest request) {
        Result<?> result = facade.createOaDraft(callerMapper.from(authentication), formId, request);
        return result.isSuccess() ? ResponseEntity.status(HttpStatus.CREATED).body(result.getData())
                : ResponseEntityUtil.assembleResponse(result);
    }
}
