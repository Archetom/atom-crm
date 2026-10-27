package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.AttachmentDomainException;
import io.github.archetom.crm.domain.model.ProductImageDraftState;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductImageDraftTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 0, 0);
    private static final MemberId UPLOADER = new MemberId(2L);

    @Test
    void draftIsPrivateToItsUploaderAndCanOnlyBeConsumedOnce() {
        ProductImageDraft draft = ProductImageDraft.create(new TenantId(1L), UPLOADER,
                NOW.plusMinutes(30), NOW);

        assertTrue(draft.isActiveFor(UPLOADER, NOW.plusSeconds(1)));
        assertFalse(draft.isActiveFor(new MemberId(3L), NOW.plusSeconds(1)));
        assertThrows(AttachmentDomainException.class,
                () -> draft.consume(new MemberId(3L), NOW.plusSeconds(2)));

        draft.consume(UPLOADER, NOW.plusSeconds(3));
        assertEquals(ProductImageDraftState.CONSUMED, draft.getState());
        assertThrows(AttachmentDomainException.class, () -> draft.consume(UPLOADER, NOW.plusSeconds(4)));
    }

    @Test
    void activeDraftExpiresAtItsExactDeadline() {
        ProductImageDraft draft = ProductImageDraft.create(new TenantId(1L), UPLOADER,
                NOW.plusMinutes(30), NOW);

        assertFalse(draft.expire(NOW.plusMinutes(29).plusSeconds(59)));
        assertTrue(draft.expire(NOW.plusMinutes(30)));
        assertEquals(ProductImageDraftState.EXPIRED, draft.getState());
    }
}
