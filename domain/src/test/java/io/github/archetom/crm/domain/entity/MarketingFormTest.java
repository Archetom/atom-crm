package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.MarketingFormDomainException;
import io.github.archetom.crm.domain.model.MarketingFormStatus;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketingFormTest {

    @Test
    void lifecycleKeepsPublicIdentityAndRequiresDeactivationBeforeDeletion() {
        MarketingForm form = MarketingForm.create(new TenantId(9L), new MemberId(7L),
                new MemberId(8L), "abcdefghijklmnopqrstuvwx", "Website leads",
                "Talk to sales", null, null, null, List.of(field("lead_name", 20)));

        assertEquals(MarketingFormStatus.INACTIVE, form.getStatus());
        assertEquals("Submit", form.getSubmitButtonText());
        assertEquals("Submitted successfully", form.getSuccessMessage());

        form.activate();
        assertTrue(form.isActive());
        assertThrows(MarketingFormDomainException.class, form::delete);

        form.deactivate();
        form.delete();
        assertTrue(form.isDeleted());
        assertThrows(MarketingFormDomainException.class, form::activate);
    }

    @Test
    void fieldsAreBoundedUniqueAndDeterministicallyOrdered() {
        MarketingForm form = MarketingForm.create(new TenantId(9L), new MemberId(7L),
                new MemberId(8L), "abcdefghijklmnopqrstuvwx", "Website leads",
                "Talk to sales", null, "Send", "Thanks", List.of(
                        field("email", 20), field("lead_name", 10)));

        assertEquals(List.of("lead_name", "email"), form.getFields().stream()
                .map(value -> value.fieldCode().getValue()).toList());
        assertThrows(MarketingFormDomainException.class, () -> MarketingForm.create(
                new TenantId(9L), new MemberId(7L), new MemberId(8L),
                "zyxwvutsrqponmlkjihgfedc", "Duplicate", "Duplicate", null,
                null, null, List.of(field("lead_name", 10), field("lead_name", 20))));
    }

    private MarketingFormField field(String code, int order) {
        return new MarketingFormField(new FieldCode(code), code, null, null, true, order);
    }
}
