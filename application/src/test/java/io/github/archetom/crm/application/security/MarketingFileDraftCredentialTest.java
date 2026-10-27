package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.entity.MarketingFileDraft;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketingFileDraftCredentialTest {

    @Test
    void acceptsOnlyTheIssuedCanonicalPurposeSeparatedCredential() {
        MarketingFileDraftCredential.Issued issued = MarketingFileDraftCredential.issue();
        LocalDateTime now = LocalDateTime.of(2026, 7, 23, 0, 0);
        MarketingFileDraft draft = MarketingFileDraft.create(new TenantId(1L), new MarketingFormId(2L),
                3L, issued.digest(), now.plusMinutes(30), now);

        assertDoesNotThrow(() -> MarketingFileDraftCredential.requireMatches(issued.secret(), draft));
        assertThrows(NonRetryableApplicationException.class,
                () -> MarketingFileDraftCredential.requireMatches("Bearer " + issued.secret(), draft));
        assertThrows(NonRetryableApplicationException.class,
                () -> MarketingFileDraftCredential.requireMatches(
                        issued.secret() + "=", draft));
    }
}
