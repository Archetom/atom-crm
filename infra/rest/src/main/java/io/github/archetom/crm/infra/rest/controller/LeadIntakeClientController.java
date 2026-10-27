package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadIntakeClientCreateRequest;
import io.github.archetom.crm.api.dto.request.LeadIntakeClientDeactivateRequest;
import io.github.archetom.crm.api.dto.response.LeadIntakeClientCreatedResponse;
import io.github.archetom.crm.api.dto.response.LeadIntakeClientResponse;
import io.github.archetom.crm.api.facade.LeadIntakeClientFacade;
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
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/v1/lead-intake-clients")
@RequiredArgsConstructor
@Validated
@Tag(name = "Lead intake clients", description = "Hash-only public lead submission credentials")
public class LeadIntakeClientController {

    private final LeadIntakeClientFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping
    @Operation(summary = "List tenant lead intake API-key clients without credentials")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = LeadIntakeClientResponse.class)))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> list(Authentication authentication) {
        return response(facade.list(caller(authentication)));
    }

    @PostMapping
    @Operation(summary = "Create a lead intake API-key client")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = LeadIntakeClientCreatedResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "422", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> create(Authentication authentication,
                                    @RequestBody @Valid LeadIntakeClientCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @DeleteMapping("/{clientId}")
    @Operation(summary = "Deactivate a lead intake API-key client")
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(
                    schema = @Schema(implementation = LeadIntakeClientResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "403", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "404", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class))),
            @ApiResponse(responseCode = "409", content = @Content(
                    schema = @Schema(implementation = RestErrorResult.class)))
    })
    public ResponseEntity<?> deactivate(Authentication authentication, @PathVariable @Positive Long clientId,
                                        @RequestParam @PositiveOrZero Long version) {
        return response(facade.deactivate(caller(authentication), clientId,
                new LeadIntakeClientDeactivateRequest().setVersion(version)));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
