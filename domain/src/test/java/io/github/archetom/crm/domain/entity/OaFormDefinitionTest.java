package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.OaFormDomainException;
import io.github.archetom.crm.domain.model.OaFormStatus;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OaFormDefinitionTest {

    @Test
    void normalizesCodeOrdersFieldsAndRequiresInactiveDeletion() {
        OaFormDefinition form = OaFormDefinition.create(
                new TenantId(9L), new MemberId(7L), "  Ｌｅａｖｅ  ",
                "Leave request", "Internal leave request",
                new ApprovalDefinitionId(3L), List.of(
                        field("cf_reason", false, 20),
                        field("request_title", true, 10)));

        assertEquals("Ｌｅａｖｅ", form.getCode());
        assertEquals("leave", form.getNormalizedCode());
        assertEquals(List.of("request_title", "cf_reason"), form.getFields().stream()
                .map(value -> value.fieldCode().getValue()).toList());
        assertEquals(OaFormStatus.INACTIVE, form.getStatus());

        form.activate();
        assertThrows(OaFormDomainException.class, form::delete);
        form.deactivate();
        form.delete();
        assertTrue(form.isDeleted());
        assertThrows(OaFormDomainException.class, form::activate);
    }

    @Test
    void rejectsMissingOptionalOrDuplicateRequestTitleAndFieldOverflow() {
        assertThrows(OaFormDomainException.class, () -> create(List.of(
                field("request_title", false, 10))));
        assertThrows(OaFormDomainException.class, () -> create(List.of(
                field("request_title", true, 10),
                field("request_title", true, 20))));

        List<OaFormField> fields = new ArrayList<>();
        fields.add(field("request_title", true, 0));
        for (int index = 0; index < OaFormDefinition.MAX_FIELDS; index++) {
            fields.add(field("cf_field_" + index, false, index + 1));
        }
        assertThrows(OaFormDomainException.class, () -> create(fields));
    }

    private OaFormDefinition create(List<OaFormField> fields) {
        return OaFormDefinition.create(new TenantId(9L), new MemberId(7L),
                "leave", "Leave request", null, new ApprovalDefinitionId(3L), fields);
    }

    private OaFormField field(String code, boolean required, int order) {
        return new OaFormField(new FieldCode(code), required, order);
    }
}
