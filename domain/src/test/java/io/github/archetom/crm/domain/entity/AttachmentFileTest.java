package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.model.AttachmentContentEvidence;
import io.github.archetom.crm.domain.model.AttachmentPublicationState;
import io.github.archetom.crm.domain.model.AttachmentInspectionState;
import io.github.archetom.crm.domain.model.AttachmentUploadOrigin;
import io.github.archetom.crm.domain.valueobject.MarketingFileDraftId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttachmentFileTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 20, 0, 0);

    @Test
    void cleanStoredBytesPublishOnlyAfterAllAxesAreEligible() {
        AttachmentFile file = AttachmentFile.createIntent(new TenantId(1L), new MemberId(2L),
                "quote.pdf", "application/pdf", 4, null, NOW);
        file.acceptStored(new AttachmentContentEvidence(4, new byte[32], 1), "application/pdf", NOW);
        file.markInspectionClean(NOW);
        file.publish(NOW);
        assertEquals(AttachmentPublicationState.AVAILABLE, file.getPublicationState());
        file.withdraw(NOW.plusSeconds(1));
        assertEquals(AttachmentPublicationState.WITHDRAWN, file.getPublicationState());
    }

    @Test
    void rejectedEvidenceIsStrongestAndCannotBePublished() {
        AttachmentFile file = AttachmentFile.createIntent(new TenantId(1L), new MemberId(2L),
                "quote.pdf", "application/pdf", 4, null, NOW);
        file.acceptStored(new AttachmentContentEvidence(4, new byte[32], 1), "application/pdf", NOW);
        file.markInspectionRejected("THREAT", NOW);
        assertThrows(RuntimeException.class, () -> file.markInspectionClean(NOW));
        assertThrows(RuntimeException.class, () -> file.publish(NOW));
    }

    @Test
    void withdrawnPendingInspectionCanReleaseItsSlotOnlyOnce() {
        AttachmentFile file = AttachmentFile.createIntent(new TenantId(1L), new MemberId(2L),
                "quote.pdf", "application/pdf", 4, null, NOW);
        file.acceptStored(new AttachmentContentEvidence(4, new byte[32], 1), "application/pdf", NOW);
        file.withdraw(NOW.plusSeconds(1));
        org.junit.jupiter.api.Assertions.assertTrue(file.abandonPendingInspection("WITHDRAWN", NOW.plusSeconds(2)));
        org.junit.jupiter.api.Assertions.assertFalse(file.abandonPendingInspection("WITHDRAWN", NOW.plusSeconds(3)));
        assertEquals(AttachmentInspectionState.INDETERMINATE, file.getInspectionState());
    }

    @Test
    void anonymousMarketingCandidateHasAnExactDraftOriginAndNeverAnUploaderMember() {
        MarketingFileDraftId draftId = new MarketingFileDraftId(7L);
        AttachmentFile file = AttachmentFile.createPublicMarketingIntent(new TenantId(1L), draftId,
                "portfolio.pdf", "application/pdf", 4, null, NOW);

        AttachmentUploadOrigin.PublicMarketingForm origin = assertInstanceOf(
                AttachmentUploadOrigin.PublicMarketingForm.class, file.getUploadOrigin());
        assertEquals(draftId, origin.draftId());
        assertNull(file.getUploaderMemberId());
        assertFalse(file.isUploadedBy(new MemberId(2L)));
    }
}
