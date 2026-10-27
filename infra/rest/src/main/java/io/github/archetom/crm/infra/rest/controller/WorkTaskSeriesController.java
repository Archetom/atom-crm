package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesCreateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskSeriesVersionRequest;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesDetailResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesListPageResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesMutationResultResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskSeriesOccurrencePageResponse;
import io.github.archetom.crm.api.facade.WorkTaskSeriesFacade;
import io.github.archetom.crm.infra.rest.result.RestErrorResult;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/v1/work/task-series")
@RequiredArgsConstructor
@Validated
@Tag(name = "Work task series", description = "Tenant-scoped controlled Work recurrence")
public class WorkTaskSeriesController {
    private final WorkTaskSeriesFacade facade;
    private final AuthenticatedCallerMapper callerMapper;
    @GetMapping @Operation(summary = "Query visible Work task series")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = WorkTaskSeriesListPageResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> query(Authentication authentication, @RequestParam(defaultValue = "1") @Positive Integer page,
                                   @RequestParam(defaultValue = "20") @Positive Integer size) { return response(facade.query(caller(authentication), page, size)); }
    @GetMapping("/{seriesId}") @Operation(summary = "Get a visible Work task series")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = WorkTaskSeriesDetailResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> get(Authentication authentication, @PathVariable @Positive Long seriesId) { return response(facade.get(caller(authentication), seriesId)); }
    @PostMapping @Operation(summary = "Create a draft Work task series")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = WorkTaskSeriesMutationResultResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> create(Authentication authentication, @RequestBody @Valid WorkTaskSeriesCreateRequest request) { return response(facade.create(caller(authentication), request)); }
    @PutMapping("/{seriesId}") @Operation(summary = "Update future occurrences of a Work task series")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = WorkTaskSeriesMutationResultResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> update(Authentication authentication, @PathVariable @Positive Long seriesId, @RequestBody @Valid WorkTaskSeriesUpdateRequest request) { return response(facade.update(caller(authentication), seriesId, request)); }
    @PostMapping("/{seriesId}/activate") @Operation(summary = "Activate a Work task series")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = WorkTaskSeriesMutationResultResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> activate(Authentication authentication, @PathVariable @Positive Long seriesId, @RequestBody @Valid WorkTaskSeriesVersionRequest request) { return response(facade.activate(caller(authentication), seriesId, request)); }
    @PostMapping("/{seriesId}/pause") @Operation(summary = "Pause a Work task series")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = WorkTaskSeriesMutationResultResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> pause(Authentication authentication, @PathVariable @Positive Long seriesId, @RequestBody @Valid WorkTaskSeriesVersionRequest request) { return response(facade.pause(caller(authentication), seriesId, request)); }
    @DeleteMapping("/{seriesId}") @Operation(summary = "Stop future Work task series occurrences")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = WorkTaskSeriesMutationResultResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable @Positive Long seriesId, @RequestParam @NotNull @Min(0) Long version) { return response(facade.delete(caller(authentication), seriesId, version)); }
    @GetMapping("/{seriesId}/occurrences") @Operation(summary = "Page immutable Work task series occurrences")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = WorkTaskSeriesOccurrencePageResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> occurrences(Authentication authentication, @PathVariable @Positive Long seriesId,
                                         @RequestParam(defaultValue = "1") @Positive Integer page,
                                         @RequestParam(defaultValue = "20") @Positive Integer size) { return response(facade.occurrences(caller(authentication), seriesId, page, size)); }
    private AuthenticatedCaller caller(Authentication authentication) { return callerMapper.from(authentication); }
    private ResponseEntity<?> response(Result<?> result) { return ResponseEntityUtil.assembleResponse(result); }
}
