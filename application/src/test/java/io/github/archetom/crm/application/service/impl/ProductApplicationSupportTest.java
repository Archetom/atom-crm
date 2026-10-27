package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.vo.ProductEditableBaselineVO;
import io.github.archetom.crm.domain.entity.Product;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.ProductAvailability;
import io.github.archetom.crm.domain.model.ProductStatus;
import io.github.archetom.crm.domain.policy.ProductFieldCatalog;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ProductCategoryId;
import io.github.archetom.crm.domain.valueobject.ProductId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ProductApplicationSupportTest {

    private final ProductApplicationSupport support = new ProductApplicationSupport(
            mock(FieldDefinitionRepository.class), mock(EffectiveFieldAccessResolver.class),
            mock(OrganizationMembershipRepository.class), mock(DepartmentRepository.class));

    @Test
    void returnsEveryReplacementValueWhenAllFieldsAreReadableUnmaskedAndWritable() {
        ProductEditableBaselineVO baseline = support.editableBaseline(product(), writableFields());

        assertTrue(baseline.editable());
        assertEquals(1L, baseline.id());
        assertEquals(4L, baseline.version());
        assertEquals("Starter", baseline.name());
        assertEquals("STARTER", baseline.code());
        assertEquals(9L, baseline.categoryId());
        assertEquals("license", baseline.unit());
        assertEquals(new BigDecimal("99.90"), baseline.listPrice());
        assertEquals("Base package", baseline.description());
    }

    @Test
    void withholdsEveryBusinessValueWhenOneReplacementFieldIsMasked() {
        Map<FieldCode, EffectiveFieldAccess> accesses = new HashMap<>(writableFields());
        accesses.put(new FieldCode(ProductFieldCatalog.NAME),
                new EffectiveFieldAccess(
                        io.github.archetom.crm.domain.model.FieldAccessLevel.WRITE,
                        FieldMaskType.PARTIAL));

        ProductEditableBaselineVO baseline = support.editableBaseline(product(), accesses);

        assertFalse(baseline.editable());
        assertEquals(1L, baseline.id());
        assertEquals(4L, baseline.version());
        assertNull(baseline.name());
        assertNull(baseline.code());
        assertNull(baseline.categoryId());
        assertNull(baseline.unit());
        assertNull(baseline.listPrice());
        assertNull(baseline.description());
    }

    private static Map<FieldCode, EffectiveFieldAccess> writableFields() {
        Map<FieldCode, EffectiveFieldAccess> accesses = new HashMap<>();
        List.of(ProductFieldCatalog.NAME, ProductFieldCatalog.CODE,
                        ProductFieldCatalog.CATEGORY, ProductFieldCatalog.UNIT,
                        ProductFieldCatalog.LIST_PRICE, ProductFieldCatalog.DESCRIPTION)
                .forEach(code -> accesses.put(new FieldCode(code), EffectiveFieldAccess.owner()));
        return accesses;
    }

    private static Product product() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 7, 12, 0);
        return Product.reconstitute(new Product.Snapshot(new ProductId(1L), new TenantId(1L),
                new ProductCategoryId(9L), new MemberId(2L), "Starter", "STARTER", "starter",
                "license", new BigDecimal("99.90"), "Base package",
                ProductAvailability.AVAILABLE, ProductStatus.ACTIVE, List.of(), 4L, now, now));
    }
}
