package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.domain.model.AttachmentTargetType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttachmentApplicationSupportTest {
    private final AttachmentApplicationSupport support = new AttachmentApplicationSupport();

    @Test
    void normalizesOnlySingleValidRangesBeforeStorageOpen() {
        assertEquals(512, support.singleRange("bytes=512-1535", 4096).offset());
        assertEquals(1024, support.singleRange("bytes=512-1535", 4096).length());
        assertEquals(3840, support.singleRange("bytes=-256", 4096).offset());
        assertNull(support.singleRange(null, 4096));
        assertThrows(RuntimeException.class, () -> support.singleRange("bytes=0-1,4-5", 4096));
        assertThrows(RuntimeException.class, () -> support.singleRange("bytes=4096-", 4096));
    }

    @Test
    void filenameAndChecksumContractsFailClosed() {
        assertEquals("quote.pdf", support.displayName(" quote.pdf "));
        assertThrows(RuntimeException.class, () -> support.displayName("../quote.pdf"));
        assertThrows(RuntimeException.class, () -> support.displayName("quote.pdf\r\nX: y"));
        assertThrows(RuntimeException.class, () -> support.expectedSha256("not-a-canonical-digest"));
    }

    @Test
    void mapsTheClosedMarketingActivityAndOaRouteTokens() {
        assertEquals(AttachmentTargetType.MARKETING_ACTIVITY,
                support.target("marketing-activity", 9L).type());
        assertEquals(AttachmentTargetType.OA_ANNOUNCEMENT,
                support.target("oa-announcement", 9L).type());
    }
}
