package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketingFormPublicIdTest {

    @Test
    void generatedIdentifierRoundTripsThroughCanonicalTenantLocator() {
        TenantId tenantId = new TenantId(999999L);
        MarketingFormPublicId.Generated generated = MarketingFormPublicId.generate(tenantId);

        assertTrue(generated.value().matches("[A-Za-z0-9_-]{11}\\.[A-Za-z0-9_-]{24}"));
        MarketingFormPublicId.Parts parsed = MarketingFormPublicId.parse(generated.value())
                .orElseThrow();
        assertEquals(tenantId, parsed.tenantId());
        assertEquals(generated.publicKey(), parsed.publicKey());
        assertEquals(generated.value(), MarketingFormPublicId.value(
                parsed.tenantId(), parsed.publicKey()));
    }

    @Test
    void malformedOrNonCanonicalIdentifiersFailClosed() {
        assertTrue(MarketingFormPublicId.parse(null).isEmpty());
        assertTrue(MarketingFormPublicId.parse("AAAAAAAAAAA.short").isEmpty());
        assertTrue(MarketingFormPublicId.parse(
                "///////////.abcdefghijklmnopqrstuvwx").isEmpty());
        assertTrue(MarketingFormPublicId.parse(
                "AAAAAAAAAAA.abcdefghijklmnopqrstuvwx.extra").isEmpty());
    }
}
