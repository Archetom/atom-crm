package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ReceivableDomainException;
import io.github.archetom.crm.domain.model.ReceivablePlanStatus;
import io.github.archetom.crm.domain.model.ReceivableStatus;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReceivablePlanId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReceivableModelTest {
    private static final TenantId TENANT = new TenantId(7L);
    private static final MemberId OWNER = new MemberId(11L);
    private static final CustomerId CUSTOMER = new CustomerId(13L);
    private static final ContractId CONTRACT = new ContractId(17L);

    @Test
    void receiptNormalizesMoneyAndRejectsUnsupportedPrecision() {
        Receivable value = Receivable.create(TENANT, OWNER, CUSTOMER, CONTRACT,
                new ReceivablePlanId(19L), "RCV-100", LocalDate.of(2026, 7, 18),
                "WIRE", new BigDecimal("10.00"), null, List.of());

        assertEquals(new BigDecimal("10.00"), value.getAmount());
        assertThrows(ReceivableDomainException.class, () -> value.replaceDetails(
                null, LocalDate.of(2026, 7, 18), "WIRE", new BigDecimal("1.001"),
                null, List.of()));
    }

    @Test
    void receiptAndPlanUseSoftDeleteStates() {
        Receivable value = Receivable.create(TENANT, OWNER, CUSTOMER, CONTRACT,
                null, "RCV-101", LocalDate.of(2026, 7, 18), "WIRE",
                BigDecimal.ONE, null, List.of());
        value.delete();
        assertEquals(ReceivableStatus.DELETED, value.getStatus());
        assertThrows(ReceivableDomainException.class, () -> value.changeOwner(OWNER));

        ReceivablePlan plan = ReceivablePlan.create(TENANT, OWNER, CUSTOMER, CONTRACT,
                1, LocalDate.of(2026, 8, 1), new BigDecimal("20.00"), "WIRE", 0,
                null, List.of());
        plan.delete();
        assertEquals(ReceivablePlanStatus.DELETED, plan.getStatus());
    }

    @Test
    void planBoundsInstallmentReminderAndDates() {
        assertThrows(ReceivableDomainException.class, () -> ReceivablePlan.create(
                TENANT, OWNER, CUSTOMER, CONTRACT, 0, LocalDate.of(2026, 8, 1),
                BigDecimal.ONE, "WIRE", null, null, List.of()));
        assertThrows(ReceivableDomainException.class, () -> ReceivablePlan.create(
                TENANT, OWNER, CUSTOMER, CONTRACT, 1, LocalDate.of(999, 12, 31),
                BigDecimal.ONE, "WIRE", null, null, List.of()));
    }
}
