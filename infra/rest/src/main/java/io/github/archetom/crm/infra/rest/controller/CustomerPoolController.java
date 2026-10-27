package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CustomerPoolAssignRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolCreateRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolCustomerBatchRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolCustomerQueryRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolTransferRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolUpdateRequest;
import io.github.archetom.crm.api.dto.response.CustomerPoolCustomerPageResponse;
import io.github.archetom.crm.api.dto.response.CustomerPoolPageResponse;
import io.github.archetom.crm.api.dto.response.CustomerPoolResponse;
import io.github.archetom.crm.api.dto.response.CustomerResponse;
import io.github.archetom.crm.api.facade.CustomerPoolFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
@Validated
@RequestMapping("/api/v1/customer-pools")
@Tag(name = "Customer pools")
public class CustomerPoolController {

    private final CustomerPoolFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    public CustomerPoolController(
            CustomerPoolFacade facade, AuthenticatedCallerMapper callerMapper) {
        this.facade = facade;
        this.callerMapper = callerMapper;
    }

    @GetMapping
    @Operation(summary = "Query customer-pool configuration")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = CustomerPoolPageResponse.class)))
    public ResponseEntity<?> query(
            Authentication authentication,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) Integer size) {
        return response(facade.query(caller(authentication), page, size));
    }

    @GetMapping("/catalog")
    @Operation(summary = "List customer pools accessible to the caller")
    @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(
            schema = @Schema(implementation = CustomerPoolResponse.class))))
    public ResponseEntity<?> catalog(Authentication authentication) {
        return response(facade.catalog(caller(authentication)));
    }

    @PostMapping
    @Operation(summary = "Create a customer pool")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = CustomerPoolResponse.class)))
    public ResponseEntity<?> create(
            Authentication authentication, @RequestBody @Valid CustomerPoolCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @GetMapping("/{poolId}")
    @Operation(summary = "Get customer-pool configuration")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = CustomerPoolResponse.class)))
    public ResponseEntity<?> get(
            Authentication authentication, @PathVariable @Positive Long poolId) {
        return response(facade.get(caller(authentication), poolId));
    }

    @PutMapping("/{poolId}")
    @Operation(summary = "Update customer-pool configuration")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = CustomerPoolResponse.class)))
    public ResponseEntity<?> update(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @RequestBody @Valid CustomerPoolUpdateRequest request) {
        return response(facade.update(caller(authentication), poolId, request));
    }

    @PutMapping("/{poolId}/status")
    @Operation(summary = "Activate or deactivate a customer pool")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = CustomerPoolResponse.class)))
    public ResponseEntity<?> updateStatus(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @RequestBody @Valid CustomerPoolStatusUpdateRequest request) {
        return response(facade.updateStatus(caller(authentication), poolId, request));
    }

    @DeleteMapping("/{poolId}")
    @Operation(summary = "Soft-delete an empty customer pool")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Void.class)))
    public ResponseEntity<?> delete(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.delete(caller(authentication), poolId, version));
    }

    @PostMapping("/{poolId}/transfer")
    @Operation(summary = "Transfer all customers to another active pool")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Void.class)))
    public ResponseEntity<?> transfer(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @RequestBody @Valid CustomerPoolTransferRequest request) {
        return response(facade.transfer(caller(authentication), poolId, request));
    }

    @GetMapping("/{poolId}/customers")
    @Operation(summary = "Query customers in an accessible pool")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = CustomerPoolCustomerPageResponse.class)))
    public ResponseEntity<?> customers(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @Valid CustomerPoolCustomerQueryRequest request) {
        return response(facade.customers(caller(authentication), poolId, request));
    }

    @GetMapping("/{poolId}/customers/{customerId}")
    @Operation(summary = "Get one customer in an accessible pool")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = CustomerResponse.class)))
    public ResponseEntity<?> customer(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @PathVariable @Positive Long customerId) {
        return response(facade.customer(caller(authentication), poolId, customerId));
    }

    @PostMapping("/{poolId}/customers/release")
    @Operation(summary = "Release visible active customers to a pool")
    @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(
            schema = @Schema(implementation = CustomerResponse.class))))
    public ResponseEntity<?> release(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @RequestBody @Valid CustomerPoolCustomerBatchRequest request) {
        return response(facade.release(caller(authentication), poolId, request));
    }

    @PostMapping("/{poolId}/customers/claim")
    @Operation(summary = "Claim customers from an accessible pool")
    @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(
            schema = @Schema(implementation = CustomerResponse.class))))
    public ResponseEntity<?> claim(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @RequestBody @Valid CustomerPoolCustomerBatchRequest request) {
        return response(facade.claim(caller(authentication), poolId, request));
    }

    @PostMapping("/{poolId}/customers/assign")
    @Operation(summary = "Assign pool customers to an active tenant member")
    @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(
            schema = @Schema(implementation = CustomerResponse.class))))
    public ResponseEntity<?> assign(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @RequestBody @Valid CustomerPoolAssignRequest request) {
        return response(facade.assign(caller(authentication), poolId, request));
    }

    @DeleteMapping("/{poolId}/customers")
    @Operation(summary = "Soft-delete customers from a pool")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Void.class)))
    public ResponseEntity<?> deleteCustomers(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @RequestBody @Valid CustomerPoolCustomerBatchRequest request) {
        return response(facade.deleteCustomers(caller(authentication), poolId, request));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
