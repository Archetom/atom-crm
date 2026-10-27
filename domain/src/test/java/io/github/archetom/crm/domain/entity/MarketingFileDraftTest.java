package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.AttachmentDomainException;
import io.github.archetom.crm.domain.model.MarketingFileDraftState;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketingFileDraftTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 23, 0, 0);

    @Test
    void activeDraftEnforcesCandidateAndByteBoundsThenConsumesOnlyOnce() {
        MarketingFileDraft draft = MarketingFileDraft.create(new TenantId(1L), new MarketingFormId(2L),
                3L, new byte[32], NOW.plusMinutes(30), NOW);

        draft.reserve(4, 0, 10, 32, NOW.plusSeconds(1));
        draft.settleActual(4, 32, NOW.plusSeconds(2));
        assertEquals(4L, draft.getReservedBytes());
        assertEquals(4L, draft.getActualBytes());
        assertThrows(AttachmentDomainException.class,
                () -> draft.reserve(29, 1, 10, 32, NOW.plusSeconds(3)));
        assertThrows(AttachmentDomainException.class,
                () -> draft.reserve(1, 10, 10, 32, NOW.plusSeconds(3)));

        draft.consume(NOW.plusSeconds(4));
        assertEquals(MarketingFileDraftState.CONSUMED, draft.getState());
        assertThrows(AttachmentDomainException.class, () -> draft.consume(NOW.plusSeconds(5)));
    }
}
