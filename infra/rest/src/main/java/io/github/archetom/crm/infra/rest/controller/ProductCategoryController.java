package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ProductCategoryCreateRequest;
import io.github.archetom.crm.api.dto.request.ProductCategoryUpdateRequest;
import io.github.archetom.crm.api.facade.ProductFacade;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/product-categories")
@RequiredArgsConstructor
@Validated
@Tag(name = "Product categories", description = "Tenant product category tree")
public class ProductCategoryController {

    private final ProductFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping
    @Operation(summary = "Get the product category tree")
    public ResponseEntity<?> tree(Authentication authentication) {
        return response(facade.categoryTree(caller(authentication)));
    }

    @GetMapping("/{categoryId}")
    @Operation(summary = "Get a product category")
    public ResponseEntity<?> get(
            Authentication authentication,
            @PathVariable @Positive Long categoryId) {
        return response(facade.getCategory(caller(authentication), categoryId));
    }

    @PostMapping
    @Operation(summary = "Create a product category")
    public ResponseEntity<?> create(
            Authentication authentication,
            @RequestBody @Valid ProductCategoryCreateRequest request) {
        return response(facade.createCategory(caller(authentication), request));
    }

    @PutMapping("/{categoryId}")
    @Operation(summary = "Update or move a product category")
    public ResponseEntity<?> update(
            Authentication authentication,
            @PathVariable @Positive Long categoryId,
            @RequestBody @Valid ProductCategoryUpdateRequest request) {
        return response(facade.updateCategory(
                caller(authentication), categoryId, request));
    }

    @DeleteMapping("/{categoryId}")
    @Operation(summary = "Soft-delete an unused product category")
    public ResponseEntity<?> delete(
            Authentication authentication,
            @PathVariable @Positive Long categoryId,
            @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.deleteCategory(
                caller(authentication), categoryId, version));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
