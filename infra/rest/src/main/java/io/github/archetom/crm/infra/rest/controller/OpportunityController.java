package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OpportunityBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.OpportunityBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityContactsUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityOutcomeUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPrimaryContactUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityQueryRequest;
import io.github.archetom.crm.api.dto.request.OpportunityStageUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityProductsUpdateRequest;
import io.github.archetom.crm.api.dto.response.OpportunityEditableBaselineResponse;
import io.github.archetom.crm.api.dto.response.OpportunityCollaboratorBaselineResponse;
import io.github.archetom.crm.api.dto.response.OpportunityContactsBaselineResponse;
import io.github.archetom.crm.api.dto.response.OpportunityPageResponse;
import io.github.archetom.crm.api.dto.response.OpportunityProductsResponse;
import io.github.archetom.crm.api.dto.response.OpportunityResponse;
import io.github.archetom.crm.api.facade.OpportunityFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
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
@RequestMapping("/api/v1/opportunities")
@RequiredArgsConstructor
@Validated
@Tag(name = "Opportunities", description = "Tenant-scoped opportunity aggregate operations")
public class OpportunityController {

    private final OpportunityFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping("/query")
    @Operation(summary = "Query visible opportunities")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OpportunityPageResponse.class)))
    public ResponseEntity<?> query(
            Authentication authentication,
            @RequestBody @Valid OpportunityQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @PostMapping("/summary")
    @Operation(summary = "Summarize visible opportunities")
    public ResponseEntity<?> summary(
            Authentication authentication,
            @RequestBody @Valid OpportunityQueryRequest request) {
        return response(facade.summary(caller(authentication), request));
    }

    @PostMapping
    @Operation(summary = "Create an opportunity")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OpportunityResponse.class)))
    public ResponseEntity<?> create(
            Authentication authentication,
            @RequestBody @Valid OpportunityCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @GetMapping("/{opportunityId}/editable-baseline")
    @Operation(summary = "Get an authoritative complete opportunity replacement baseline",
            description = "Returns business values only when every replace field is readable, "
                    + "unmasked, and writable for the caller.")
    @ApiResponse(responseCode = "200", description = "Opportunity editable baseline",
            content = @Content(schema = @Schema(
                    implementation = OpportunityEditableBaselineResponse.class)))
    public ResponseEntity<?> editableBaseline(
            Authentication authentication,
            @PathVariable @Positive Long opportunityId) {
        return response(facade.editableBaseline(caller(authentication), opportunityId));
    }

    @GetMapping("/{opportunityId}/collaborators")
    @Operation(summary = "Get the authoritative opportunity collaborator baseline", description =
            "Requires opportunities:assign owner scope and returns the complete replace baseline,"
                    + " its version, and the verified caller collaborator state.")
    @ApiResponse(responseCode = "200", description = "Opportunity collaborator baseline",
            content = @Content(schema = @Schema(
                    implementation = OpportunityCollaboratorBaselineResponse.class)))
    public ResponseEntity<?> collaborators(
            Authentication authentication, @PathVariable @Positive Long opportunityId) {
        return response(facade.collaborators(caller(authentication), opportunityId));
    }

    @GetMapping("/{opportunityId}/contacts/baseline")
    @Operation(summary = "Get the authoritative opportunity contacts replacement baseline", description =
            "Returns associations only when the relation fields, customer reference, and every "
                    + "related contact are currently safe for the caller to preserve and replace.")
    @ApiResponse(responseCode = "200", description = "Opportunity contacts replacement baseline",
            content = @Content(schema = @Schema(
                    implementation = OpportunityContactsBaselineResponse.class)))
    public ResponseEntity<?> contactsBaseline(
            Authentication authentication, @PathVariable @Positive Long opportunityId) {
        return response(facade.contactsBaseline(caller(authentication), opportunityId));
    }

    @GetMapping("/{opportunityId}")
    @Operation(summary = "Get a visible opportunity")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OpportunityResponse.class)))
    public ResponseEntity<?> get(
            Authentication authentication,
            @PathVariable @Positive Long opportunityId) {
        return response(facade.get(caller(authentication), opportunityId));
    }

    @PutMapping("/{opportunityId}")
    @Operation(summary = "Update opportunity details")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OpportunityResponse.class)))
    public ResponseEntity<?> update(
            Authentication authentication,
            @PathVariable @Positive Long opportunityId,
            @RequestBody @Valid OpportunityUpdateRequest request) {
        return response(facade.update(caller(authentication), opportunityId, request));
    }

    @DeleteMapping("/{opportunityId}")
    @Operation(summary = "Soft-delete an opportunity")
    public ResponseEntity<?> delete(
            Authentication authentication,
            @PathVariable @Positive Long opportunityId,
            @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.delete(caller(authentication), opportunityId, version));
    }

    @PutMapping("/batch/owner")
    @Operation(summary = "Change the owner of a bounded opportunity batch atomically")
    public ResponseEntity<?> updateOwners(
            Authentication authentication,
            @RequestBody @Valid OpportunityBatchOwnerUpdateRequest request) {
        return response(facade.updateOwners(caller(authentication), request));
    }

    @PostMapping("/batch/delete")
    @Operation(summary = "Delete a bounded opportunity batch atomically")
    public ResponseEntity<?> deleteBatch(
            Authentication authentication,
            @RequestBody @Valid OpportunityBatchDeleteRequest request) {
        return response(facade.deleteBatch(caller(authentication), request));
    }

    @PutMapping("/{opportunityId}/owner")
    @Operation(summary = "Change opportunity owner")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OpportunityResponse.class)))
    public ResponseEntity<?> updateOwner(
            Authentication authentication,
            @PathVariable @Positive Long opportunityId,
            @RequestBody @Valid OpportunityOwnerUpdateRequest request) {
        return response(facade.updateOwner(caller(authentication), opportunityId, request));
    }

    @PutMapping("/{opportunityId}/collaborators")
    @Operation(summary = "Replace opportunity collaborators")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OpportunityResponse.class)))
    public ResponseEntity<?> replaceCollaborators(
            Authentication authentication,
            @PathVariable @Positive Long opportunityId,
            @RequestBody @Valid OpportunityCollaboratorsUpdateRequest request) {
        return response(facade.replaceCollaborators(
                caller(authentication), opportunityId, request));
    }

    @DeleteMapping("/{opportunityId}/collaborators/me")
    @Operation(summary = "Exit the opportunity collaborators")
    public ResponseEntity<?> exitCollaborator(
            Authentication authentication,
            @PathVariable @Positive Long opportunityId,
            @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.exitCollaborator(
                caller(authentication), opportunityId, version));
    }

    @PutMapping("/{opportunityId}/contacts")
    @Operation(summary = "Replace opportunity contacts")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OpportunityResponse.class)))
    public ResponseEntity<?> replaceContacts(
            Authentication authentication,
            @PathVariable @Positive Long opportunityId,
            @RequestBody @Valid OpportunityContactsUpdateRequest request) {
        return response(facade.replaceContacts(
                caller(authentication), opportunityId, request));
    }

    @PutMapping("/{opportunityId}/primary-contact")
    @Operation(summary = "Set or clear the primary opportunity contact")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OpportunityResponse.class)))
    public ResponseEntity<?> updatePrimaryContact(
            Authentication authentication,
            @PathVariable @Positive Long opportunityId,
            @RequestBody @Valid OpportunityPrimaryContactUpdateRequest request) {
        return response(facade.updatePrimaryContact(
                caller(authentication), opportunityId, request));
    }

    @PostMapping("/{opportunityId}/contacts/query")
    @Operation(summary = "Query visible contacts related to an opportunity")
    public ResponseEntity<?> contacts(
            Authentication authentication,
            @PathVariable @Positive Long opportunityId,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) Integer size) {
        return response(facade.contacts(
                caller(authentication), opportunityId, page, size));
    }

    @PutMapping("/{opportunityId}/stage")
    @Operation(summary = "Change opportunity sales stage")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OpportunityResponse.class)))
    public ResponseEntity<?> updateStage(
            Authentication authentication,
            @PathVariable @Positive Long opportunityId,
            @RequestBody @Valid OpportunityStageUpdateRequest request) {
        return response(facade.updateStage(caller(authentication), opportunityId, request));
    }

    @PutMapping("/{opportunityId}/outcome")
    @Operation(summary = "Close or reopen an opportunity")
    @ApiResponse(responseCode = "200", content = @Content(
            schema = @Schema(implementation = OpportunityResponse.class)))
    public ResponseEntity<?> updateOutcome(
            Authentication authentication,
            @PathVariable @Positive Long opportunityId,
            @RequestBody @Valid OpportunityOutcomeUpdateRequest request) {
        return response(facade.updateOutcome(
                caller(authentication), opportunityId, request));
    }

    @PutMapping("/{opportunityId}/star")
    @Operation(summary = "Set the caller's personal opportunity star")
    public ResponseEntity<?> setStarred(
            Authentication authentication,
            @PathVariable @Positive Long opportunityId,
            @RequestBody @Valid OpportunityStarUpdateRequest request) {
        return response(facade.setStarred(
                caller(authentication), opportunityId, request));
    }

    @GetMapping("/{opportunityId}/products")
    @Operation(summary = "Get opportunity product snapshots")
    @ApiResponse(responseCode = "200", description = "Complete versioned opportunity product snapshot",
            content = @Content(schema = @Schema(implementation = OpportunityProductsResponse.class)))
    public ResponseEntity<?> products(
            Authentication authentication,
            @PathVariable @Positive Long opportunityId) {
        return response(facade.products(caller(authentication), opportunityId));
    }

    @PutMapping("/{opportunityId}/products")
    @Operation(summary = "Replace opportunity products")
    @ApiResponse(responseCode = "200", description = "Updated complete opportunity product snapshot",
            content = @Content(schema = @Schema(implementation = OpportunityProductsResponse.class)))
    public ResponseEntity<?> replaceProducts(
            Authentication authentication,
            @PathVariable @Positive Long opportunityId,
            @RequestBody @Valid OpportunityProductsUpdateRequest request) {
        return response(facade.replaceProducts(
                caller(authentication), opportunityId, request));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
