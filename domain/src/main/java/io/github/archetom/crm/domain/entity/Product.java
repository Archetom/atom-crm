package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.ProductDomainException;
import io.github.archetom.crm.domain.model.ProductAvailability;
import io.github.archetom.crm.domain.model.ProductStatus;
import io.github.archetom.crm.domain.policy.IdentifierKeyNormalizer;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ProductCategoryId;
import io.github.archetom.crm.domain.valueobject.ProductId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

/** Tenant-owned versioned product and its custom values. */
@Getter
public class Product extends AggregateRoot<ProductId> {

    public static final int MAX_CUSTOM_FIELDS = 200;

    private ProductId id;
    private TenantId tenantId;
    private ProductCategoryId categoryId;
    private MemberId ownerMemberId;
    private String name;
    private String code;
    private String codeKey;
    private String unit;
    private BigDecimal listPrice;
    private String description;
    private ProductAvailability availability;
    private ProductStatus status;
    private List<ProductFieldValue> customFieldValues = List.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private Product() {
    }

    public record Snapshot(
            ProductId id,
            TenantId tenantId,
            ProductCategoryId categoryId,
            MemberId ownerMemberId,
            String name,
            String code,
            String codeKey,
            String unit,
            BigDecimal listPrice,
            String description,
            ProductAvailability availability,
            ProductStatus status,
            List<ProductFieldValue> customFieldValues,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static Product create(
            TenantId tenantId,
            ProductCategoryId categoryId,
            MemberId ownerMemberId,
            String name,
            String code,
            String unit,
            BigDecimal listPrice,
            String description,
            ProductAvailability availability,
            Collection<ProductFieldValue> customFieldValues) {
        Product product = new Product();
        product.tenantId = requireTenant(tenantId);
        product.ownerMemberId = requireOwner(ownerMemberId, ProductStatus.ACTIVE);
        product.status = ProductStatus.ACTIVE;
        product.applyDetails(categoryId, name, code, unit, listPrice,
                description, availability, customFieldValues);
        product.createdTime = product.updatedTime;
        return product;
    }

    public static Product reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new ProductDomainException("product snapshot is required");
        }
        Product product = new Product();
        product.id = snapshot.id();
        product.tenantId = requireTenant(snapshot.tenantId());
        product.status = snapshot.status() == null
                ? ProductStatus.DELETED : snapshot.status();
        product.ownerMemberId = requireOwner(snapshot.ownerMemberId(), product.status);
        product.categoryId = snapshot.categoryId();
        product.name = requireName(snapshot.name());
        product.code = requireCode(snapshot.code());
        product.codeKey = normalizeCodeKey(product.code);
        if (snapshot.codeKey() == null || !product.codeKey.equals(snapshot.codeKey())) {
            throw new ProductDomainException("stored product code key is invalid");
        }
        product.unit = optional(snapshot.unit(), 50, "product unit");
        product.listPrice = requirePrice(snapshot.listPrice());
        product.description = optional(
                snapshot.description(), 2000, "product description");
        product.availability = snapshot.availability() == null
                ? ProductAvailability.UNAVAILABLE : snapshot.availability();
        product.customFieldValues = validateCustomFields(snapshot.customFieldValues());
        product.restoreVersion(snapshot.version());
        product.createdTime = snapshot.createdTime();
        product.updatedTime = snapshot.updatedTime();
        return product;
    }

    public void replaceDetails(
            ProductCategoryId categoryId,
            String name,
            String code,
            String unit,
            BigDecimal listPrice,
            String description,
            Collection<ProductFieldValue> customFieldValues) {
        ensureActive();
        applyDetails(categoryId, name, code, unit, listPrice, description,
                availability, customFieldValues);
    }

    public void changeOwner(MemberId ownerMemberId) {
        ensureActive();
        this.ownerMemberId = requireOwner(ownerMemberId, status);
        touch();
    }

    public void changeAvailability(ProductAvailability availability) {
        ensureActive();
        if (availability == null) {
            throw new ProductDomainException("product availability is required");
        }
        this.availability = availability;
        touch();
    }

    public void delete() {
        ensureActive();
        status = ProductStatus.DELETED;
        availability = ProductAvailability.UNAVAILABLE;
        touch();
    }

    public boolean isActive() {
        return status == ProductStatus.ACTIVE;
    }

    public boolean isAvailable() {
        return isActive() && availability == ProductAvailability.AVAILABLE;
    }

    public void onPersisted(
            ProductId id, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new ProductDomainException("persisted product ID is invalid");
        }
        this.id = id;
        restoreVersion(version);
        this.createdTime = createdTime == null ? this.createdTime : createdTime;
        this.updatedTime = updatedTime == null ? this.updatedTime : updatedTime;
    }

    private void applyDetails(
            ProductCategoryId categoryId,
            String name,
            String code,
            String unit,
            BigDecimal listPrice,
            String description,
            ProductAvailability availability,
            Collection<ProductFieldValue> customFieldValues) {
        String nextName = requireName(name);
        String nextCode = requireCode(code);
        String nextUnit = optional(unit, 50, "product unit");
        BigDecimal nextPrice = requirePrice(listPrice);
        String nextDescription = optional(
                description, 2000, "product description");
        ProductAvailability nextAvailability = availability == null
                ? ProductAvailability.AVAILABLE : availability;
        List<ProductFieldValue> nextFields = validateCustomFields(customFieldValues);

        this.categoryId = categoryId;
        this.name = nextName;
        this.code = nextCode;
        this.codeKey = normalizeCodeKey(nextCode);
        this.unit = nextUnit;
        this.listPrice = nextPrice;
        this.description = nextDescription;
        this.availability = nextAvailability;
        this.customFieldValues = nextFields;
        touch();
    }

    private void ensureActive() {
        if (!isActive()) {
            throw new ProductDomainException("only active products can change");
        }
    }

    private void touch() {
        updatedTime = LocalDateTime.now();
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new ProductDomainException("product tenant is required");
        }
        return value;
    }

    private static MemberId requireOwner(MemberId value, ProductStatus status) {
        if (value == null && status == ProductStatus.ACTIVE) {
            throw new ProductDomainException("active product owner is required");
        }
        return value;
    }

    private static String requireName(String value) {
        String normalized = optional(value, 200, "product name");
        if (normalized == null) {
            throw new ProductDomainException("product name is required");
        }
        return normalized;
    }

    private static String requireCode(String value) {
        String normalized = optional(value, 100, "product code");
        if (normalized == null) {
            throw new ProductDomainException("product code is required");
        }
        return normalized;
    }

    private static String normalizeCodeKey(String value) {
        String normalized = IdentifierKeyNormalizer.normalize(value);
        if (normalized.length() > 100) {
            throw new ProductDomainException(
                    "normalized product code is too long");
        }
        return normalized;
    }

    private static BigDecimal requirePrice(BigDecimal value) {
        if (value == null) {
            throw new ProductDomainException("product list price is required");
        }
        BigDecimal normalized;
        try {
            normalized = value.setScale(2);
        } catch (ArithmeticException exception) {
            throw new ProductDomainException(
                    "product list price supports at most two decimal places");
        }
        if (normalized.signum() < 0 || normalized.precision() > 18) {
            throw new ProductDomainException("product list price is invalid");
        }
        return normalized;
    }

    private static String optional(String value, int max, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new ProductDomainException(description + " is too long");
        }
        return normalized;
    }

    private static List<ProductFieldValue> validateCustomFields(
            Collection<ProductFieldValue> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_CUSTOM_FIELDS) {
            throw new ProductDomainException(
                    "product custom fields exceed the supported limit");
        }
        LinkedHashMap<FieldCode, ProductFieldValue> unique = new LinkedHashMap<>();
        for (ProductFieldValue value : values) {
            if (value == null || unique.putIfAbsent(value.fieldCode(), value) != null) {
                throw new ProductDomainException("product custom fields are invalid");
            }
        }
        return List.copyOf(unique.values());
    }
}
