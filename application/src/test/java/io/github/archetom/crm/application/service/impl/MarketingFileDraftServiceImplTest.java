package io.github.archetom.crm.application.service.impl;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketingFileDraftServiceImplTest {

    @Test
    void capsIntentAndUsesDraftDeadlineAsTheRetentionBase() {
        Instant draftDeadline = Instant.parse("2026-07-23T10:05:00Z");

        assertEquals(draftDeadline, MarketingFileDraftServiceImpl.noLaterThan(
                Instant.parse("2026-07-23T10:10:00Z"), draftDeadline));
        assertEquals(Instant.parse("2026-07-23T10:02:00Z"),
                MarketingFileDraftServiceImpl.noLaterThan(
                        Instant.parse("2026-07-23T10:02:00Z"), draftDeadline));
        assertEquals(Instant.parse("2026-07-23T10:35:00Z"),
                MarketingFileDraftServiceImpl.retentionDeadline(draftDeadline, Duration.ofMinutes(30)));
    }
}
