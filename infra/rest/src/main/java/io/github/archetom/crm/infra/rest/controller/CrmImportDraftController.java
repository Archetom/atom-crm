package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.dto.request.CrmImportDraftConfirmRequest;
import io.github.archetom.crm.api.dto.request.CrmImportDraftCreateRequest;
import io.github.archetom.crm.api.dto.response.CrmImportConfirmResponse;
import io.github.archetom.crm.api.dto.response.CrmImportDraftResponse;
import io.github.archetom.crm.api.dto.response.CrmImportHistoryPageResponse;
import io.github.archetom.crm.api.dto.response.CrmImportPreviewResponse;
import io.github.archetom.crm.api.dto.response.CrmImportTemplateResponse;
import io.github.archetom.crm.api.facade.CrmImportDraftFacade;
import io.github.archetom.crm.application.export.CrmImportErrorReportDocument;
import io.github.archetom.crm.application.service.CrmImportDraftService;
import io.github.archetom.crm.infra.rest.advice.ResultResponseException;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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

/** Private, staged source-file workflow for Lead and Contact CSV imports. */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/crm-import-drafts")
@Tag(name = "CRM import drafts", description = "Controlled Lead and Contact CSV import lifecycle")
public class CrmImportDraftController {
    private final CrmImportDraftFacade facade;
    private final CrmImportDraftService service;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping
    @Operation(summary = "Create a private CRM CSV import draft")
    @ApiResponse(responseCode = "201", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = CrmImportDraftResponse.class)))
    public ResponseEntity<?> create(Authentication authentication,
                                    @RequestBody @Valid CrmImportDraftCreateRequest request) {
        Result<CrmImportDraftResponse> result = facade.create(callerMapper.from(authentication), request);
        return result.isSuccess() ? ResponseEntity.status(HttpStatus.CREATED).body(result.getData())
                : ResponseEntityUtil.assembleResponse(result);
    }

    @GetMapping("/templates/{objectType}")
    @Operation(summary = "Get a server-defined CRM import CSV template")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = CrmImportTemplateResponse.class)))
    public ResponseEntity<?> template(Authentication authentication, @PathVariable String objectType) {
        return ResponseEntityUtil.assembleResponse(facade.template(callerMapper.from(authentication), objectType));
    }

    @GetMapping("/history/{objectType}")
    @Operation(summary = "List recent completed CRM imports owned by the current member")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = CrmImportHistoryPageResponse.class)))
    public ResponseEntity<?> history(Authentication authentication, @PathVariable String objectType,
                                     @RequestParam(defaultValue = "1") @Positive Long page,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(50) Long size) {
        return ResponseEntityUtil.assembleResponse(
                facade.history(callerMapper.from(authentication), objectType, page, size));
    }

    @GetMapping("/{draftId}/preview")
    @Operation(summary = "Parse and validate a clean staged CRM CSV without persisting rows")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = CrmImportPreviewResponse.class)))
    public ResponseEntity<?> preview(Authentication authentication, @PathVariable @Positive Long draftId) {
        return ResponseEntityUtil.assembleResponse(facade.preview(callerMapper.from(authentication), draftId));
    }

    @GetMapping(value = "/{draftId}/error-report", produces = "text/csv;charset=UTF-8")
    @Operation(summary = "Download a private CRM import error report without source values")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "text/csv;charset=UTF-8",
            schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> errorReport(Authentication authentication, @PathVariable @Positive Long draftId) {
        Result<CrmImportErrorReportDocument> result = service.errorReport(callerMapper.from(authentication), draftId);
        if (!result.isSuccess()) throw new ResultResponseException(result);
        byte[] content = result.getData().content();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"crm-import-errors.csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(content);
    }

    @PostMapping("/{draftId}/confirm")
    @Operation(summary = "Atomically create all validated CRM CSV rows and withdraw the source")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = CrmImportConfirmResponse.class)))
    public ResponseEntity<?> confirm(Authentication authentication, @PathVariable @Positive Long draftId,
                                     @RequestBody @Valid CrmImportDraftConfirmRequest request) {
        return ResponseEntityUtil.assembleResponse(facade.confirm(callerMapper.from(authentication), draftId, request));
    }

    @DeleteMapping("/{draftId}")
    @Operation(summary = "Withdraw a private CRM import draft and its source attachment")
    @ApiResponse(responseCode = "204", description = "CRM import draft withdrawn")
    public ResponseEntity<?> withdraw(Authentication authentication, @PathVariable @Positive Long draftId,
                                      @RequestParam @NotNull @Min(0) Long version) {
        Result<Void> result = facade.withdraw(callerMapper.from(authentication), draftId, version);
        return result.isSuccess() ? ResponseEntity.noContent().build() : ResponseEntityUtil.assembleResponse(result);
    }
}
