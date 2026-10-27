package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LeadIntakeApiKeyTest {

    @Test
    void generatesIndependentHighEntropyCredentialsAcceptedByTheParser() {
        TenantId tenantId = new TenantId(9L);
        LeadIntakeApiKey.Generated first = LeadIntakeApiKey.generate(tenantId);
        LeadIntakeApiKey.Generated second = LeadIntakeApiKey.generate(tenantId);

        assertEquals(11, first.tenantLocator().length());
        assertEquals(24, first.keyId().length());
        assertEquals(43, first.secret().length());
        LeadIntakeApiKey.Parts parsed = LeadIntakeApiKey.parse(first.value()).orElseThrow();
        assertEquals(tenantId, parsed.tenantId());
        assertEquals(first.keyId(), parsed.keyId());
        assertNotEquals(first.value(), second.value());
    }

    @Test
    void rejectsALocatorWithNonCanonicalUnusedBase64Bits() {
        LeadIntakeApiKey.Generated key = LeadIntakeApiKey.generate(new TenantId(9L));
        String locator = key.tenantLocator();
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        int lastIndex = alphabet.indexOf(locator.charAt(locator.length() - 1));
        int alternateIndex = (lastIndex & 0b111100) | ((lastIndex + 1) & 0b11);
        String nonCanonicalLocator = locator.substring(0, locator.length() - 1)
                + alphabet.charAt(alternateIndex);

        assertFalse(LeadIntakeApiKey.parse(nonCanonicalLocator + "." + key.keyId()
                + "." + key.secret()).isPresent());
    }
}
