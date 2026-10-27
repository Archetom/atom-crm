package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Product;
import io.github.archetom.crm.domain.entity.ProductCategory;
import io.github.archetom.crm.domain.entity.ProductFieldValue;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.ProductAvailability;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FieldDefinitionId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ProductCategoryId;
import io.github.archetom.crm.domain.valueobject.ProductId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ProductCategoryPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ProductFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ProductPO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProductPOConverterTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final LocalDateTime CREATED =
            LocalDateTime.of(2026, 7, 17, 12, 0);

    @Test
    void productAndCustomValueRoundTripWithoutEvents() {
        ProductPOConverter converter = new ProductPOConverter(new ObjectMapper());
        FieldDefinition field = FieldDefinition.createCustom(
                TENANT, BusinessObjectType.PRODUCT,
                new FieldCode("cf_edition"), "Edition", null,
                CustomFieldType.TEXT, false, false, true, 1, null, List.of());
        field.onPersisted(new FieldDefinitionId(31L), 0L, CREATED, CREATED);

        Product product = Product.create(
                TENANT, new ProductCategoryId(3L), new MemberId(11L),
                "Service Plan", "PLAN-A", "seat", new BigDecimal("25.00"),
                "Annual plan", ProductAvailability.AVAILABLE,
                List.of(new ProductFieldValue(field.getCode(), List.of("pro"))));
        product.onPersisted(new ProductId(21L), 4L, CREATED, CREATED.plusHours(1));

        ProductPO root = converter.toPO(product);
        ProductFieldValuePO value = converter.toPO(
                TENANT, product.getId(), product.getCustomFieldValues().getFirst(), field);
        Product restored = converter.toDomain(root, List.of(value), Map.of(31L, field));

        assertEquals(product.getId(), restored.getId());
        assertEquals(product.getCategoryId(), restored.getCategoryId());
        assertEquals(product.getOwnerMemberId(), restored.getOwnerMemberId());
        assertEquals(product.getCodeKey(), restored.getCodeKey());
        assertEquals(product.getListPrice(), restored.getListPrice());
        assertEquals(product.getCustomFieldValues(), restored.getCustomFieldValues());
        assertEquals(4L, restored.getVersion());
        assertFalse(restored.hasDomainEvents());
    }

    @Test
    void categoryRoundTripsWithoutEvents() {
        ProductCategoryPOConverter converter = new ProductCategoryPOConverter();
        ProductCategory category = ProductCategory.create(
                TENANT, new ProductCategoryId(2L), "Software");
        category.onPersisted(
                new ProductCategoryId(3L), 2L, CREATED, CREATED.plusMinutes(1));

        ProductCategoryPO root = converter.toPO(category);
        ProductCategory restored = converter.toDomain(root);

        assertEquals(category.getId(), restored.getId());
        assertEquals(category.getParentCategoryId(), restored.getParentCategoryId());
        assertEquals(category.getNameKey(), restored.getNameKey());
        assertEquals(2L, restored.getVersion());
        assertFalse(restored.hasDomainEvents());
    }
}
