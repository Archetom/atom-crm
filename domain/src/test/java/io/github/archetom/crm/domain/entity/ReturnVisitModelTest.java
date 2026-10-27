package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ReturnVisitDomainException;
import io.github.archetom.crm.domain.model.ReturnVisitForm;
import io.github.archetom.crm.domain.model.ReturnVisitSatisfaction;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReturnVisitModelTest {
    @Test
    void activeVisitCanBeReplacedAndSoftDeleteStopsFurtherChanges() {
        ReturnVisit value = ReturnVisit.create(new TenantId(1L), new MemberId(2L), new CustomerId(3L),
                null, null, "RV-1", LocalDateTime.now(), null,
                ReturnVisitForm.PHONE, ReturnVisitSatisfaction.SATISFIED, List.of());
        assertDoesNotThrow(() -> value.replace(new ContactId(4L), new ContractId(5L),
                LocalDateTime.now(), "Completed", ReturnVisitForm.EMAIL,
                ReturnVisitSatisfaction.VERY_SATISFIED, List.of()));
        value.delete();
        assertThrows(ReturnVisitDomainException.class, () -> value.changeOwner(new MemberId(6L)));
    }

    @Test
    void requiredRootValuesFailClosed() {
        assertThrows(ReturnVisitDomainException.class, () -> ReturnVisit.create(new TenantId(1L),
                new MemberId(2L), new CustomerId(3L), null, null, "", LocalDateTime.now(), null,
                null, null, List.of()));
    }

    @Test
    void invalidReferenceEnumsFailClosed() {
        assertThrows(ReturnVisitDomainException.class, () -> ReturnVisitForm.optional("VOICE"));
        assertThrows(ReturnVisitDomainException.class, () -> ReturnVisitSatisfaction.optional("GREAT"));
    }
}
