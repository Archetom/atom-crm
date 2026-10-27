package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.AttachmentDomainException;
import io.github.archetom.crm.domain.model.CrmImportDraftState;
import io.github.archetom.crm.domain.model.CrmImportObjectType;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrmImportDraftTest {
    private static final TenantId TENANT = new TenantId(7L);
    private static final MemberId UPLOADER = new MemberId(11L);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 12, 0);

    @Test
    void productDraftMayCompleteWithNoCreatedRowsWhenEveryCodeWasAlreadyPresent() {
        CrmImportDraft draft = CrmImportDraft.create(TENANT, UPLOADER, CrmImportObjectType.PRODUCT,
                NOW.plusMinutes(5), NOW);

        draft.consume(UPLOADER, 0, NOW.plusSeconds(1));

        assertEquals(CrmImportDraftState.CONSUMED, draft.getState());
        assertEquals(0, draft.getImportedRows());
    }

    @Test
    void importDraftNeverAcceptsANegativeCompletionCount() {
        CrmImportDraft draft = CrmImportDraft.create(TENANT, UPLOADER, CrmImportObjectType.PRODUCT,
                NOW.plusMinutes(5), NOW);

        assertThrows(AttachmentDomainException.class, () -> draft.consume(UPLOADER, -1, NOW.plusSeconds(1)));
    }
}
