package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.MarketingForm;
import io.github.archetom.crm.domain.entity.MarketingFormField;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.MarketingFormFieldPO;
import io.github.archetom.crm.infra.persistence.mysql.po.MarketingFormPO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MarketingFormPOConverterTest {

    @Test
    void activeFormAndOrderedFieldsRoundTripWithoutEvents() {
        MarketingFormPOConverter converter = new MarketingFormPOConverter();
        TenantId tenantId = new TenantId(9L);
        MarketingForm form = MarketingForm.create(
                tenantId, new MemberId(11L), new MemberId(12L),
                "0123456789abcdefghijklmn", "Website leads", "Contact sales",
                "Tell us about your project", "Send", "We will contact you",
                List.of(
                        new MarketingFormField(new FieldCode("email"), "Email",
                                "name@example.com", null, false, 20),
                        new MarketingFormField(new FieldCode("lead_name"), "Name",
                                null, "Company or contact name", true, 10)));
        LocalDateTime created = LocalDateTime.of(2026, 7, 18, 12, 0);
        LocalDateTime updated = created.plusHours(1);
        form.onPersisted(new MarketingFormId(21L), 3L, created, updated);
        form.activate();

        MarketingFormPO root = converter.toPO(form);
        List<MarketingFormFieldPO> fields = form.getFields().stream()
                .map(field -> converter.toPO(tenantId, form.getId(), field))
                .toList();
        MarketingForm restored = converter.toDomain(root, fields);

        assertEquals(form.getId(), restored.getId());
        assertEquals(form.getTenantId(), restored.getTenantId());
        assertEquals(form.getCreatedByMemberId(), restored.getCreatedByMemberId());
        assertEquals(form.getOwnerMemberId(), restored.getOwnerMemberId());
        assertEquals(form.getPublicKey(), restored.getPublicKey());
        assertEquals(form.getName(), restored.getName());
        assertEquals(form.getTitle(), restored.getTitle());
        assertEquals(form.getDescription(), restored.getDescription());
        assertEquals(form.getSubmitButtonText(), restored.getSubmitButtonText());
        assertEquals(form.getSuccessMessage(), restored.getSuccessMessage());
        assertEquals(form.getStatus(), restored.getStatus());
        assertEquals(form.getFields(), restored.getFields());
        assertEquals(form.getVersion(), restored.getVersion());
        assertEquals(form.getCreatedTime(), restored.getCreatedTime());
        assertEquals(form.getUpdatedTime(), restored.getUpdatedTime());
        assertFalse(restored.hasDomainEvents());
    }
}
