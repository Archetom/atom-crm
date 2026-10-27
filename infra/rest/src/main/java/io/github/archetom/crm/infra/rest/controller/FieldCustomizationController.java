package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldLayoutUpdateRequest;
import io.github.archetom.crm.api.dto.request.FieldUpdateRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldPolicyReplaceRequest;
import io.github.archetom.crm.api.facade.FieldCustomizationFacade;
import io.github.archetom.crm.api.dto.response.BusinessObjectResponse;
import io.github.archetom.crm.api.dto.response.FieldDefinitionResponse;
import io.github.archetom.crm.api.dto.response.FieldLayoutResponse;
import io.github.archetom.crm.api.dto.response.RoleFieldPolicyResponse;
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
@RequestMapping("/api/v1/fields")
@RequiredArgsConstructor
@Tag(name = "Field customization", description = "Tenant field metadata and bounded preferences")
public class FieldCustomizationController {

    private final FieldCustomizationFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping("/objects")
    @Operation(summary = "List customizable business objects")
    @ApiResponse(responseCode = "200", content = @Content(
            array = @ArraySchema(schema = @Schema(implementation = BusinessObjectResponse.class))))
    public ResponseEntity<?> listObjects(Authentication authentication) {
        return response(facade.listObjects(caller(authentication)));
    }

    @GetMapping("/objects/{objectType}")
    @Operation(summary = "List field definitions")
    @ApiResponse(responseCode = "200", content = @Content(
            array = @ArraySchema(schema = @Schema(implementation = FieldDefinitionResponse.class))))
    public ResponseEntity<?> listFields(Authentication authentication,
                                        @PathVariable String objectType,
                                        @RequestParam(defaultValue = "false") boolean includeInactive) {
        return response(facade.listFields(caller(authentication), objectType, includeInactive));
    }

    @PostMapping("/objects/{objectType}")
    @Operation(summary = "Create a custom field")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FieldDefinitionResponse.class)))
    public ResponseEntity<?> createField(Authentication authentication,
                                         @PathVariable String objectType,
                                         @RequestBody @Valid FieldCreateRequest request) {
        return response(facade.createField(caller(authentication), objectType, request));
    }

    @PutMapping("/objects/{objectType}/{fieldId}")
    @Operation(summary = "Update custom field metadata")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FieldDefinitionResponse.class)))
    public ResponseEntity<?> updateField(Authentication authentication,
                                         @PathVariable String objectType,
                                         @PathVariable Long fieldId,
                                         @RequestBody @Valid FieldUpdateRequest request) {
        return response(facade.updateField(caller(authentication), objectType, fieldId, request));
    }

    @DeleteMapping("/objects/{objectType}/{fieldId}")
    @Operation(summary = "Deactivate a custom field")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Void.class)))
    public ResponseEntity<?> deactivateField(Authentication authentication,
                                             @PathVariable String objectType,
                                             @PathVariable Long fieldId,
                                             @RequestParam Long version) {
        return response(facade.deactivateField(caller(authentication), objectType, fieldId, version));
    }

    @GetMapping("/objects/{objectType}/layout")
    @Operation(summary = "Get the caller's field layout")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FieldLayoutResponse.class)))
    public ResponseEntity<?> getLayout(Authentication authentication, @PathVariable String objectType) {
        return response(facade.getLayout(caller(authentication), objectType));
    }

    @PutMapping("/objects/{objectType}/layout")
    @Operation(summary = "Update the caller's field layout")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = FieldLayoutResponse.class)))
    public ResponseEntity<?> updateLayout(Authentication authentication,
                                          @PathVariable String objectType,
                                          @RequestBody @Valid FieldLayoutUpdateRequest request) {
        return response(facade.updateLayout(caller(authentication), objectType, request));
    }

    @GetMapping("/roles/{roleId}/objects/{objectType}/access")
    @Operation(summary = "Get a tenant role's field policy")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = RoleFieldPolicyResponse.class)))
    public ResponseEntity<?> getRolePolicy(Authentication authentication,
                                           @PathVariable Long roleId,
                                           @PathVariable String objectType) {
        return response(facade.getRolePolicy(caller(authentication), roleId, objectType));
    }

    @PutMapping("/roles/{roleId}/objects/{objectType}/access")
    @Operation(summary = "Replace a custom role's field policy")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = RoleFieldPolicyResponse.class)))
    public ResponseEntity<?> replaceRolePolicy(Authentication authentication,
                                               @PathVariable Long roleId,
                                               @PathVariable String objectType,
                                               @RequestBody @Valid RoleFieldPolicyReplaceRequest request) {
        return response(facade.replaceRolePolicy(caller(authentication), roleId, objectType, request));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
