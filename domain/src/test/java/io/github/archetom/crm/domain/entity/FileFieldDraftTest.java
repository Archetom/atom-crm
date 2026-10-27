package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.AttachmentDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.FileFieldDraftState;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaFormId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileFieldDraftTest {
    private static final TenantId TENANT = new TenantId(7L);
    private static final MemberId UPLOADER = new MemberId(11L);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 21, 12, 0);

    @Test
    void oaDraftIsPinnedToItsFormAndCanOnlyBeConsumedOnceByItsUploader() {
        FileFieldDraft draft = FileFieldDraft.create(TENANT, UPLOADER,
                BusinessObjectType.OA_REQUEST, null, new OaFormId(3L), 4L,
                new byte[32], NOW.plusMinutes(5), NOW);

        draft.consume(UPLOADER, BusinessObjectType.OA_REQUEST, NOW.plusMinutes(1));

        assertEquals(FileFieldDraftState.CONSUMED, draft.getState());
        assertThrows(AttachmentDomainException.class, () -> draft.consume(
                UPLOADER, BusinessObjectType.OA_REQUEST, NOW.plusMinutes(2)));
        assertThrows(AttachmentDomainException.class, () -> draft.consume(
                new MemberId(12L), BusinessObjectType.OA_REQUEST, NOW.plusMinutes(1)));
    }

    @Test
    void crmDraftCannotCarryAnOaFormOrOutliveItsDeadline() {
        assertThrows(AttachmentDomainException.class, () -> FileFieldDraft.create(TENANT, UPLOADER,
                BusinessObjectType.CUSTOMER, null, new OaFormId(3L), 1L,
                new byte[32], NOW.plusMinutes(5), NOW));

        FileFieldDraft draft = FileFieldDraft.create(TENANT, UPLOADER,
                BusinessObjectType.CUSTOMER, null, null, null,
                new byte[32], NOW.plusMinutes(1), NOW);
        assertThrows(AttachmentDomainException.class, () -> draft.withdraw(
                UPLOADER, NOW.plusMinutes(1)));
    }

    @Test
    void productAndReturnVisitCanUsePreCreateFileDrafts() {
        FileFieldDraft product = FileFieldDraft.create(TENANT, UPLOADER,
                BusinessObjectType.PRODUCT, null, null, null,
                new byte[32], NOW.plusMinutes(5), NOW);
        FileFieldDraft returnVisit = FileFieldDraft.create(TENANT, UPLOADER,
                BusinessObjectType.RETURN_VISIT, null, null, null,
                new byte[32], NOW.plusMinutes(5), NOW);

        assertEquals(BusinessObjectType.PRODUCT, product.getObjectType());
        assertEquals(BusinessObjectType.RETURN_VISIT, returnVisit.getObjectType());
    }
}
