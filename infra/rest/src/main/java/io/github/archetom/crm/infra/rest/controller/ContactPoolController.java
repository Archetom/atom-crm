package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContactPoolAssignRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolCreateRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolContactBatchRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolContactQueryRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolTransferRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolUpdateRequest;
import io.github.archetom.crm.api.facade.ContactPoolFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/contact-pools")
@Tag(name = "Contact pools")
public class ContactPoolController {

    private final ContactPoolFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    public ContactPoolController(
            ContactPoolFacade facade, AuthenticatedCallerMapper callerMapper) {
        this.facade = facade;
        this.callerMapper = callerMapper;
    }

    @GetMapping
    @Operation(summary = "Query contact-pool configuration")
    public ResponseEntity<?> query(
            Authentication authentication,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) Integer size) {
        return response(facade.query(caller(authentication), page, size));
    }

    @GetMapping("/catalog")
    @Operation(summary = "List contact pools accessible to the caller")
    public ResponseEntity<?> catalog(Authentication authentication) {
        return response(facade.catalog(caller(authentication)));
    }

    @PostMapping
    @Operation(summary = "Create a contact pool")
    public ResponseEntity<?> create(
            Authentication authentication, @RequestBody @Valid ContactPoolCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @GetMapping("/{poolId}")
    @Operation(summary = "Get contact-pool configuration")
    public ResponseEntity<?> get(
            Authentication authentication, @PathVariable @Positive Long poolId) {
        return response(facade.get(caller(authentication), poolId));
    }

    @PutMapping("/{poolId}")
    @Operation(summary = "Update contact-pool configuration")
    public ResponseEntity<?> update(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @RequestBody @Valid ContactPoolUpdateRequest request) {
        return response(facade.update(caller(authentication), poolId, request));
    }

    @PutMapping("/{poolId}/status")
    @Operation(summary = "Activate or deactivate a contact pool")
    public ResponseEntity<?> updateStatus(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @RequestBody @Valid ContactPoolStatusUpdateRequest request) {
        return response(facade.updateStatus(caller(authentication), poolId, request));
    }

    @DeleteMapping("/{poolId}")
    @Operation(summary = "Soft-delete an empty contact pool")
    public ResponseEntity<?> delete(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.delete(caller(authentication), poolId, version));
    }

    @PostMapping("/{poolId}/transfer")
    @Operation(summary = "Transfer all contacts to another active pool")
    public ResponseEntity<?> transfer(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @RequestBody @Valid ContactPoolTransferRequest request) {
        return response(facade.transfer(caller(authentication), poolId, request));
    }

    @GetMapping("/{poolId}/contacts")
    @Operation(summary = "Query contacts in an accessible pool")
    public ResponseEntity<?> contacts(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @Valid ContactPoolContactQueryRequest request) {
        return response(facade.contacts(caller(authentication), poolId, request));
    }

    @GetMapping("/{poolId}/contacts/{contactId}")
    @Operation(summary = "Get one contact in an accessible pool")
    public ResponseEntity<?> contact(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @PathVariable @Positive Long contactId) {
        return response(facade.contact(caller(authentication), poolId, contactId));
    }

    @PostMapping("/{poolId}/contacts/release")
    @Operation(summary = "Release visible active contacts to a pool")
    public ResponseEntity<?> release(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @RequestBody @Valid ContactPoolContactBatchRequest request) {
        return response(facade.release(caller(authentication), poolId, request));
    }

    @PostMapping("/{poolId}/contacts/claim")
    @Operation(summary = "Claim contacts from an accessible pool")
    public ResponseEntity<?> claim(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @RequestBody @Valid ContactPoolContactBatchRequest request) {
        return response(facade.claim(caller(authentication), poolId, request));
    }

    @PostMapping("/{poolId}/contacts/assign")
    @Operation(summary = "Assign pool contacts to an active tenant member")
    public ResponseEntity<?> assign(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @RequestBody @Valid ContactPoolAssignRequest request) {
        return response(facade.assign(caller(authentication), poolId, request));
    }

    @DeleteMapping("/{poolId}/contacts")
    @Operation(summary = "Soft-delete contacts from a pool")
    public ResponseEntity<?> deleteContacts(
            Authentication authentication, @PathVariable @Positive Long poolId,
            @RequestBody @Valid ContactPoolContactBatchRequest request) {
        return response(facade.deleteContacts(caller(authentication), poolId, request));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
