package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.SavedViewCreateRequest;
import io.github.archetom.crm.api.dto.request.SavedViewUpdateRequest;
import io.github.archetom.crm.api.dto.response.SavedViewResponse;
import io.github.archetom.crm.api.facade.SavedViewFacade;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/saved-views")
@RequiredArgsConstructor
@Tag(name = "Saved views", description = "Current-member typed filters")
public class SavedViewController {

    private final SavedViewFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping
    @Operation(summary = "List the caller's saved views")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = SavedViewResponse.class)))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> list(Authentication authentication,
                                  @RequestParam String objectType,
                                  @RequestParam(defaultValue = "false") boolean includeHidden) {
        return response(facade.list(caller(authentication), objectType, includeHidden));
    }

    @PostMapping
    @Operation(summary = "Create a saved view")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = SavedViewResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> create(Authentication authentication,
                                    @RequestBody @Valid SavedViewCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @PutMapping("/{viewId}")
    @Operation(summary = "Update a saved view")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = SavedViewResponse.class))),
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
    public ResponseEntity<?> update(Authentication authentication, @PathVariable Long viewId,
                                    @RequestBody @Valid SavedViewUpdateRequest request) {
        return response(facade.update(caller(authentication), viewId, request));
    }

    @PutMapping("/{viewId}/default")
    @Operation(summary = "Set the caller's default view for its object")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = SavedViewResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> setDefault(Authentication authentication, @PathVariable Long viewId,
                                        @RequestParam Long version) {
        return response(facade.setDefault(caller(authentication), viewId, version));
    }

    @DeleteMapping("/{viewId}")
    @Operation(summary = "Delete a saved view")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Void.class))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long viewId,
                                    @RequestParam Long version) {
        return response(facade.delete(caller(authentication), viewId, version));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
