package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.OaRequestDomainException;
import io.github.archetom.crm.domain.policy.OaRequestFieldCatalog;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaFormId;
import io.github.archetom.crm.domain.valueobject.OaRequestId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OaRequestTest {

    @Test
    void freezesAuthorityFactsAndLinksOneApproval() {
        LocalDateTime submitted = LocalDateTime.of(2026, 7, 19, 10, 30);
        OaRequest request = OaRequest.submit(new TenantId(1L), new OaFormId(2L),
                3L, "leave", new MemberId(4L), null, "retry-1", "a".repeat(64),
                facts("Leave request", "leave", 4L, submitted), submitted);
        request.onPersisted(new OaRequestId(5L), 0L, submitted, submitted);
        request.linkApproval(new ApprovalInstanceId(6L));

        assertEquals("Leave request", request.getRequestTitle());
        assertEquals(6L, request.getApprovalInstanceId().getValue());
        assertThrows(OaRequestDomainException.class,
                () -> request.linkApproval(new ApprovalInstanceId(7L)));
    }

    @Test
    void rejectsCallerControlledAuthorityFactMismatch() {
        LocalDateTime submitted = LocalDateTime.of(2026, 7, 19, 10, 30);
        var facts = facts("Leave request", "leave", 99L, submitted);
        assertThrows(OaRequestDomainException.class, () -> OaRequest.submit(
                new TenantId(1L), new OaFormId(2L), 3L, "leave",
                new MemberId(4L), null, "retry-1", "a".repeat(64),
                facts, submitted));
    }

    private LinkedHashMap<FieldCode, List<String>> facts(
            String title, String formCode, long applicant, LocalDateTime submitted) {
        LinkedHashMap<FieldCode, List<String>> values = new LinkedHashMap<>();
        values.put(new FieldCode(OaRequestFieldCatalog.REQUEST_TITLE), List.of(title));
        values.put(new FieldCode(OaRequestFieldCatalog.FORM_CODE), List.of(formCode));
        values.put(new FieldCode(OaRequestFieldCatalog.APPLICANT_ACTOR),
                List.of(Long.toString(applicant)));
        values.put(new FieldCode(OaRequestFieldCatalog.APPLICANT_DEPARTMENT), List.of());
        values.put(new FieldCode(OaRequestFieldCatalog.SUBMITTED_TIME),
                List.of(submitted.toString()));
        return values;
    }
}
