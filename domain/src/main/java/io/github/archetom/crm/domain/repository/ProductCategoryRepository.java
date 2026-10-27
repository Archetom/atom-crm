package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.ProductCategory;
import io.github.archetom.crm.domain.valueobject.ProductCategoryId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped category-tree persistence. */
public interface ProductCategoryRepository {

    ProductCategory save(TenantId tenantId, ProductCategory category);

    Optional<ProductCategory> findById(
            TenantId tenantId, ProductCategoryId categoryId);

    Optional<ProductCategory> findActiveById(
            TenantId tenantId, ProductCategoryId categoryId);

    List<ProductCategory> findAllActive(TenantId tenantId, int limit);

    boolean existsActiveChild(
            TenantId tenantId, ProductCategoryId categoryId);
}
