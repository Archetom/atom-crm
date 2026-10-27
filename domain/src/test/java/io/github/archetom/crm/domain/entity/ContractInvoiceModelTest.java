package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ContractDomainException;
import io.github.archetom.crm.domain.exception.InvoiceDomainException;
import io.github.archetom.crm.domain.model.InvoiceProfileStatus;
import io.github.archetom.crm.domain.model.InvoiceTitleType;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ProductId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractInvoiceModelTest {
    private static final TenantId TENANT = new TenantId(7L);
    private static final MemberId OWNER = new MemberId(11L);
    private static final CustomerId CUSTOMER = new CustomerId(13L);

    @Test
    void contractRejectsNumberThatExpandsPastKeyLimitAfterNfkc() {
        assertThrows(ContractDomainException.class, () -> Contract.create(
                TENANT, OWNER, CUSTOMER, null, null, "Agreement",
                "\uFDFA".repeat(10), null, null, null, null, null, null,
                BigDecimal.ZERO, List.of(), List.of(), List.of()));
    }

    @Test
    void contractRechecksDecimalPrecisionAfterSummingValidLines() {
        ContractProductLine first = line(1L, 0);
        ContractProductLine second = line(2L, 1);

        assertThrows(ContractDomainException.class, () -> Contract.create(
                TENANT, OWNER, CUSTOMER, null, null, "Agreement", "C-100",
                null, null, null, null, null, null, BigDecimal.ZERO,
                List.of(first, second), List.of(), List.of()));
    }

    @Test
    void contractRejectsDatesOutsideMysqlDateRange() {
        assertThrows(ContractDomainException.class, () -> contract(
                LocalDate.of(999, 12, 31), null, null));
        assertThrows(ContractDomainException.class, () -> contract(
                null, LocalDate.of(999, 12, 31), null));
        assertThrows(ContractDomainException.class, () -> contract(
                null, null, LocalDate.of(10_000, 1, 1)));

        Contract contract = contract(LocalDate.of(1000, 1, 1),
                LocalDate.of(1000, 1, 1), LocalDate.of(9999, 12, 31));
        assertEquals(LocalDate.of(1000, 1, 1), contract.getOrderDate());
        assertEquals(LocalDate.of(9999, 12, 31), contract.getEndDate());
    }

    @Test
    void invoiceRejectsDatesOutsideMysqlDateRange() {
        assertThrows(InvoiceDomainException.class,
                () -> invoice(LocalDate.of(999, 12, 31)));
        Invoice invoice = invoice(LocalDate.of(1000, 1, 1));
        assertThrows(InvoiceDomainException.class, () -> invoice.issue(
                "F-100", LocalDate.of(10_000, 1, 1), null));

        Invoice issued = invoice(LocalDate.of(9999, 12, 31));
        issued.issue("F-101", LocalDate.of(9999, 12, 31), null);
        assertEquals(LocalDate.of(9999, 12, 31), issued.getIssuedDate());
    }

    @Test
    void individualInvoiceProfileUsesStatusSoftDeleteAndClearsOrganizationFields() {
        InvoiceProfile profile = InvoiceProfile.create(TENANT, CUSTOMER,
                InvoiceTitleType.INDIVIDUAL, "Personal title", "ignored-tax",
                "ignored-bank", "ignored-account", "ignored-address",
                "ignored-phone", null);

        assertEquals(InvoiceProfileStatus.ACTIVE, profile.getStatus());
        assertNull(profile.getTaxNumber());
        assertNull(profile.getBankName());
        profile.delete();
        assertEquals(InvoiceProfileStatus.DELETED, profile.getStatus());
    }

    private ContractProductLine line(long productId, int order) {
        return new ContractProductLine(new ProductId(productId), "Product", "P-" + productId,
                null, null, new BigDecimal("9999999999999999.99"),
                new BigDecimal("9999999999999999.99"), new BigDecimal("6000.0000"),
                BigDecimal.ZERO, new BigDecimal("59999999999999999940.00"), order);
    }

    private Contract contract(LocalDate orderDate, LocalDate startDate, LocalDate endDate) {
        return Contract.create(TENANT, OWNER, CUSTOMER, null, null,
                "Agreement", "C-DATE", orderDate, startDate, endDate,
                null, null, null, BigDecimal.ZERO, List.of(), List.of(), List.of());
    }

    private Invoice invoice(LocalDate requestedDate) {
        return Invoice.create(TENANT, OWNER, CUSTOMER, new ContractId(17L),
                "A-100", BigDecimal.ONE, requestedDate, "VAT",
                InvoiceTitleType.INDIVIDUAL, "Personal", null, null, null,
                null, null, null, null, null, null, List.of());
    }
}
