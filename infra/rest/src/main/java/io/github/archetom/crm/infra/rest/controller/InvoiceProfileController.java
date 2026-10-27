package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.InvoiceProfileRequest;
import io.github.archetom.crm.api.dto.request.InvoiceProfileUpdateRequest;
import io.github.archetom.crm.api.facade.InvoiceFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invoice-profiles")
@RequiredArgsConstructor
@Validated
@Tag(name = "Invoice profiles", description = "Customer invoice profile operations")
public class InvoiceProfileController {
    private final InvoiceFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping
    @Operation(summary = "List customer invoice profiles")
    public ResponseEntity<?> profiles(Authentication authentication,
                                      @RequestParam @Positive Long customerId) {
        return response(facade.profiles(caller(authentication), customerId));
    }

    @PostMapping
    @Operation(summary = "Create an invoice profile")
    public ResponseEntity<?> create(Authentication authentication,
                                    @RequestBody @Valid InvoiceProfileRequest request) {
        return response(facade.createProfile(caller(authentication), request));
    }

    @PutMapping("/{profileId}")
    @Operation(summary = "Update an invoice profile", description =
            "Omitted fields retain their current values; version is required.")
    public ResponseEntity<?> update(Authentication authentication,
                                    @PathVariable @Positive Long profileId,
                                    @RequestBody @Valid InvoiceProfileUpdateRequest request) {
        return response(facade.updateProfile(caller(authentication), profileId, request));
    }

    @PatchMapping("/{profileId}")
    @Operation(summary = "Partially update an invoice profile", description =
            "Omitted fields retain their current values; only submitted non-null fields are changed."
                    + " Version is required.")
    public ResponseEntity<?> patch(Authentication authentication,
                                   @PathVariable @Positive Long profileId,
                                   @RequestBody @Valid InvoiceProfileUpdateRequest request) {
        return response(facade.updateProfile(caller(authentication), profileId, request));
    }

    @DeleteMapping("/{profileId}")
    @Operation(summary = "Soft-delete an invoice profile")
    public ResponseEntity<?> delete(Authentication authentication,
                                    @PathVariable @Positive Long profileId,
                                    @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.deleteProfile(caller(authentication), profileId, version));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
