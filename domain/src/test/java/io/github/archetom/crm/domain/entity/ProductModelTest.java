package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.OpportunityDomainException;
import io.github.archetom.crm.domain.exception.ProductDomainException;
import io.github.archetom.crm.domain.model.ProductAvailability;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ProductCategoryId;
import io.github.archetom.crm.domain.valueobject.ProductId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductModelTest {

    private static final TenantId TENANT = new TenantId(7L);
    private static final MemberId OWNER = new MemberId(11L);

    @Test
    void productNormalizesCodePriceAndSoftDeleteState() {
        Product product = Product.create(TENANT, new ProductCategoryId(3L), OWNER,
                "  Service plan  ", "  Plan-Ａ  ", " seat ",
                new BigDecimal("12.50"), null, ProductAvailability.AVAILABLE,
                List.of());

        assertEquals("Service plan", product.getName());
        assertEquals("plan-a", product.getCodeKey());
        assertEquals(new BigDecimal("12.50"), product.getListPrice());
        product.delete();
        assertEquals(ProductAvailability.UNAVAILABLE, product.getAvailability());
        assertThrows(ProductDomainException.class,
                () -> product.changeAvailability(ProductAvailability.AVAILABLE));
    }

    @Test
    void categoryReconstitutionRejectsAStaleNormalizationKey() {
        assertThrows(ProductDomainException.class, () -> ProductCategory.reconstitute(
                new ProductCategory.Snapshot(new ProductCategoryId(1L), TENANT,
                        null, "Hardware", "different", null, 0L,
                        LocalDateTime.now(), LocalDateTime.now())));
        assertThrows(ProductDomainException.class, () -> ProductCategory.create(
                TENANT, null, "\uFDFA".repeat(200)));
        assertThrows(ProductDomainException.class, () -> Product.create(
                TENANT, null, OWNER, "Plan", "\uFDFA".repeat(100), null,
                BigDecimal.ZERO, null, ProductAvailability.AVAILABLE, List.of()));
    }

    @Test
    void opportunityProductSnapshotCalculatesAmountAndRejectsDuplicates() {
        Product product = Product.create(TENANT, null, OWNER, "Plan", "PLAN",
                "seat", new BigDecimal("20.00"), null,
                ProductAvailability.AVAILABLE, List.of());
        product.onPersisted(new ProductId(5L), 0L,
                LocalDateTime.now(), LocalDateTime.now());
        OpportunityProductLine line = OpportunityProductLine.snapshot(product,
                null, new BigDecimal("2.5000"), new BigDecimal("18.00"), 0);

        assertEquals(new BigDecimal("45.00"), line.lineAmount());
        assertEquals(new BigDecimal("10.0000"), line.discountRate());
        assertThrows(OpportunityDomainException.class,
                () -> OpportunityProductLine.snapshot(product, null, BigDecimal.ONE,
                        new BigDecimal("10000000000000000.00"), 0));

        Opportunity opportunity = Opportunity.create(TENANT, OWNER,
                new io.github.archetom.crm.domain.valueobject.CustomerId(9L),
                "Renewal", "direct", "qualification", null, null, null, null,
                List.of(), null, List.of(), List.of());
        assertThrows(OpportunityDomainException.class,
                () -> opportunity.replaceProductLines(List.of(line, line)));
    }
}
