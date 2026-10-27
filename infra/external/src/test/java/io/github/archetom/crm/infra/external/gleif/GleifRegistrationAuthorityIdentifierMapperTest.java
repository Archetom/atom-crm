package io.github.archetom.crm.infra.external.gleif;

import io.github.archetom.crm.domain.model.IdentifierStanding;
import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegistryScheme;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifier;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GleifRegistrationAuthorityIdentifierMapperTest {

    private final GleifRegistrationAuthorityIdentifierMapper mapper =
            new GleifRegistrationAuthorityIdentifierMapper();

    @Test
    void mapsTheThreePinnedRaRowsToOneCompaniesHouseIdentityType() {
        assertCompaniesHouse("RA000585", " 00000001 ", "00000001");
        assertCompaniesHouse("RA000586", " ni000001 ", "NI000001");
        assertCompaniesHouse("RA000587", " sc000001 ", "SC000001");
        assertEquals("GLEIF-RA-1.8.1/B23-CH-2026-07-20", mapper.catalogVersion());
    }

    @Test
    void rejectsUnknownRaAndMissingEntityIdWithoutGuessing() {
        assertEmpty("RA999999", "SC000001");
        assertEmpty("ra000587", "SC000001");
        assertEmpty("RA000587", null);
        assertEmpty("RA000587", "  ");
    }

    @Test
    void rejectsAValidCompaniesHouseNumberFromTheWrongRaNumberClass() {
        assertEmpty("RA000585", "SC000001");
        assertEmpty("RA000586", "00000001");
        assertEmpty("RA000586", "SC000001");
        assertEmpty("RA000587", "NI000001");
    }

    @Test
    void rejectsEntityIdsThatFailTheCompaniesHouseSchemeValidator() {
        assertEmpty("RA000585", "ZZ000001");
        assertEmpty("RA000586", "NI00001");
        assertEmpty("RA000587", "ＳＣ000001");
    }

    private void assertCompaniesHouse(String ra, String entityId, String expectedValue) {
        RegisteredIdentifier identifier = mapper.map(ra, entityId).orElseThrow();

        assertEquals(IdentityScope.UK_COMPANIES_HOUSE, identifier.getIdentityScope());
        assertEquals(RegistryScheme.GB_CH_NUMBER, identifier.getRegistryScheme());
        assertEquals(expectedValue, identifier.getCanonicalValue());
        assertEquals(IdentifierStanding.UNKNOWN, identifier.getIdentifierStanding());
    }

    private void assertEmpty(String ra, String entityId) {
        Optional<RegisteredIdentifier> identifier = mapper.map(ra, entityId);

        assertTrue(identifier.isEmpty());
    }
}
