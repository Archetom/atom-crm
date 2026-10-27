package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.ProductDomainException;
import io.github.archetom.crm.domain.model.ProductCategoryStatus;
import io.github.archetom.crm.domain.valueobject.ProductCategoryId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;

/** Versioned node in one tenant's product-category tree. */
@Getter
public class ProductCategory extends AggregateRoot<ProductCategoryId> {

    private ProductCategoryId id;
    private TenantId tenantId;
    private ProductCategoryId parentCategoryId;
    private String name;
    private String nameKey;
    private ProductCategoryStatus status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private ProductCategory() {
    }

    public record Snapshot(
            ProductCategoryId id,
            TenantId tenantId,
            ProductCategoryId parentCategoryId,
            String name,
            String nameKey,
            ProductCategoryStatus status,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static ProductCategory create(
            TenantId tenantId, ProductCategoryId parentCategoryId, String name) {
        ProductCategory category = new ProductCategory();
        category.tenantId = requireTenant(tenantId);
        category.status = ProductCategoryStatus.ACTIVE;
        category.apply(parentCategoryId, name);
        category.createdTime = category.updatedTime;
        return category;
    }

    public static ProductCategory reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new ProductDomainException("product category snapshot is required");
        }
        ProductCategory category = new ProductCategory();
        category.id = snapshot.id();
        category.tenantId = requireTenant(snapshot.tenantId());
        category.parentCategoryId = snapshot.parentCategoryId();
        category.name = requireName(snapshot.name());
        category.nameKey = normalizeKey(category.name);
        if (snapshot.nameKey() == null || !category.nameKey.equals(snapshot.nameKey())) {
            throw new ProductDomainException("stored product category key is invalid");
        }
        category.status = snapshot.status() == null
                ? ProductCategoryStatus.DELETED : snapshot.status();
        category.restoreVersion(snapshot.version());
        category.createdTime = snapshot.createdTime();
        category.updatedTime = snapshot.updatedTime();
        return category;
    }

    public void change(ProductCategoryId parentCategoryId, String name) {
        ensureActive();
        apply(parentCategoryId, name);
    }

    public void delete() {
        ensureActive();
        status = ProductCategoryStatus.DELETED;
        touch();
    }

    public boolean isActive() {
        return status == ProductCategoryStatus.ACTIVE;
    }

    public void onPersisted(
            ProductCategoryId id, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new ProductDomainException("persisted product category ID is invalid");
        }
        this.id = id;
        restoreVersion(version);
        this.createdTime = createdTime == null ? this.createdTime : createdTime;
        this.updatedTime = updatedTime == null ? this.updatedTime : updatedTime;
    }

    private void apply(ProductCategoryId parentCategoryId, String name) {
        if (id != null && parentCategoryId != null && id.sameValueAs(parentCategoryId)) {
            throw new ProductDomainException("product category cannot be its own parent");
        }
        String normalized = requireName(name);
        this.parentCategoryId = parentCategoryId;
        this.name = normalized;
        this.nameKey = normalizeKey(normalized);
        touch();
    }

    private void ensureActive() {
        if (!isActive()) {
            throw new ProductDomainException("only active product categories can change");
        }
    }

    private void touch() {
        updatedTime = LocalDateTime.now();
    }

    private static TenantId requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new ProductDomainException("product category tenant is required");
        }
        return tenantId;
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new ProductDomainException("product category name is required");
        }
        String normalized = value.trim();
        if (normalized.length() > 200) {
            throw new ProductDomainException("product category name is too long");
        }
        return normalized;
    }

    private static String normalizeKey(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        if (normalized.length() > 200) {
            throw new ProductDomainException(
                    "normalized product category name is too long");
        }
        return normalized;
    }
}
