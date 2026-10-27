package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.EmbeddedPageCreateRequest;
import io.github.archetom.crm.api.dto.request.EmbeddedPageQueryRequest;
import io.github.archetom.crm.api.dto.request.EmbeddedPageUpdateRequest;
import io.github.archetom.crm.api.dto.response.EmbeddedPageConfigurationOptionsResponse;
import io.github.archetom.crm.api.dto.response.EmbeddedPagePageResponse;
import io.github.archetom.crm.api.dto.response.EmbeddedPageResponse;
import io.github.archetom.crm.api.dto.response.RenderedEmbeddedPageResponse;
import io.github.archetom.crm.api.facade.EmbeddedPageFacade;
import io.github.archetom.crm.infra.rest.result.RestErrorResult;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/embedded-pages")
@RequiredArgsConstructor
@Tag(name = "Embedded pages", description = "Tenant-managed safe client link templates")
public class EmbeddedPageController {

    private final EmbeddedPageFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping("/configuration-options")
    @Operation(summary = "Get embedded page configuration options")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = EmbeddedPageConfigurationOptionsResponse.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> configurationOptions(Authentication authentication) {
        return response(facade.configurationOptions(caller(authentication)));
    }

    @GetMapping
    @Operation(summary = "Query tenant embedded page configuration")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = EmbeddedPagePageResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> query(Authentication authentication,
                                   @ParameterObject @ModelAttribute @Valid EmbeddedPageQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @PostMapping
    @Operation(summary = "Create an inactive embedded page")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = EmbeddedPageResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> create(Authentication authentication,
                                    @RequestBody @Valid EmbeddedPageCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @PutMapping("/{pageId}")
    @Operation(summary = "Update an embedded page")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = EmbeddedPageResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> update(Authentication authentication, @PathVariable Long pageId,
                                    @RequestBody @Valid EmbeddedPageUpdateRequest request) {
        return response(facade.update(caller(authentication), pageId, request));
    }

    @DeleteMapping("/{pageId}")
    @Operation(summary = "Delete an inactive embedded page")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Void.class))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long pageId,
                                    @RequestParam Long version) {
        return response(facade.delete(caller(authentication), pageId, version));
    }

    @GetMapping("/objects/{objectType}/{recordId}")
    @Operation(summary = "List rendered active embedded pages")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = RenderedEmbeddedPageResponse.class)))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> listActive(Authentication authentication,
                                        @PathVariable String objectType,
                                        @PathVariable Long recordId,
                                        @RequestParam String platform) {
        return response(facade.listActive(caller(authentication), objectType, recordId, platform));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
