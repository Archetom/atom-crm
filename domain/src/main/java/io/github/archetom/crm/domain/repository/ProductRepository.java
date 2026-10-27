package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Product;
import io.github.archetom.crm.domain.model.ProductQuery;
import io.github.archetom.crm.domain.valueobject.ProductCategoryId;
import io.github.archetom.crm.domain.valueobject.ProductId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Tenant-scoped product aggregate persistence. */
public interface ProductRepository {

    Product save(TenantId tenantId, Product product,
                 Collection<FieldDefinition> fieldDefinitions);

    Optional<Product> findById(TenantId tenantId, ProductId productId);

    Optional<Product> findActiveById(TenantId tenantId, ProductId productId);

    /** Checks the normalized code key while excluding soft-deleted products. */
    boolean existsActiveByCode(TenantId tenantId, String codeKey);

    /** Locks the exact root for attachment worker and parent-lifecycle ordering. */
    Optional<Product> findByIdForAttachmentMutation(TenantId tenantId, ProductId productId);

    PageResult<Product> findProducts(TenantId tenantId, ProductQuery query);

    List<Product> findAvailableByIdsForUpdate(
            TenantId tenantId, Collection<ProductId> productIds, int limit);

    boolean existsActiveByCategory(
            TenantId tenantId, ProductCategoryId categoryId);
}
