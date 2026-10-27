package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.model.IdentifierStanding;
import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegistryScheme;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifier;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegisteredIdentifierCatalogTest {

    @Test
    void canonicalizesAndChecksSchemeSpecificIdentifiers() {
        RegisteredIdentifierKey uscc = new RegisteredIdentifierKey(
                IdentityScope.CN_UNIFIED_SOCIAL_CREDIT_CODE,
                RegistryScheme.CN_USCC, " 91110000f1xture1an ");
        RegisteredIdentifierKey lei = new RegisteredIdentifierKey(
                IdentityScope.GLOBAL_LEI_SYSTEM,
                RegistryScheme.LEI, "0000syntheticfixtu42");
        RegisteredIdentifierKey companiesHouse = new RegisteredIdentifierKey(
                IdentityScope.UK_COMPANIES_HOUSE,
                RegistryScheme.GB_CH_NUMBER, " sc000001 ");
        RegisteredIdentifierKey numericCompaniesHouse = new RegisteredIdentifierKey(
                IdentityScope.UK_COMPANIES_HOUSE,
                RegistryScheme.GB_CH_NUMBER, " 00000001 ");

        assertEquals("91110000F1XTURE1AN", uscc.getCanonicalValue());
        assertEquals("0000SYNTHETICFIXTU42", lei.getCanonicalValue());
        assertEquals("SC000001", companiesHouse.getCanonicalValue());
        assertEquals("00000001", numericCompaniesHouse.getCanonicalValue());
    }

    @Test
    void rejectsBadChecksumsCharactersAndScopeSchemeMixing() {
        assertThrows(IllegalArgumentException.class, () -> new RegisteredIdentifierKey(
                IdentityScope.CN_UNIFIED_SOCIAL_CREDIT_CODE,
                RegistryScheme.CN_USCC, "91110000F1XTURE1AA"));
        assertThrows(IllegalArgumentException.class, () -> new RegisteredIdentifierKey(
                IdentityScope.CN_UNIFIED_SOCIAL_CREDIT_CODE,
                RegistryScheme.CN_USCC, "91110000SYNTHET1AQ"));
        assertThrows(IllegalArgumentException.class, () -> new RegisteredIdentifierKey(
                IdentityScope.GLOBAL_LEI_SYSTEM,
                RegistryScheme.LEI, "0000SYNTHETICFIXTU41"));
        assertThrows(IllegalArgumentException.class, () -> new RegisteredIdentifierKey(
                IdentityScope.GLOBAL_LEI_SYSTEM,
                RegistryScheme.CN_USCC, "91110000F1XTURE1AN"));
        assertThrows(IllegalArgumentException.class, () -> new RegisteredIdentifierKey(
                IdentityScope.CN_UNIFIED_SOCIAL_CREDIT_CODE,
                RegistryScheme.CN_USCC, "91AAAAAAF1XTURE1A3"));
        assertThrows(IllegalArgumentException.class, () -> new RegisteredIdentifierKey(
                IdentityScope.UK_COMPANIES_HOUSE,
                RegistryScheme.LEI, "SC000001"));
    }

    @Test
    void enforcesTheGovernedCompaniesHouseNumberClassDirectory() {
        for (String prefix : new String[]{
                "AC", "BR", "CE", "CS", "EN", "ES", "FC", "FE", "GE", "GN",
                "GS", "IC", "IP", "LP", "NA", "NC", "NF", "NI", "NL", "NO",
                "NP", "NR", "NV", "NZ", "OC", "OE", "PC", "R0", "RC", "RS",
                "SA", "SC", "SE", "SF", "SG", "SI", "SL", "SO", "SP", "SR",
                "SZ", "ZC"}) {
            assertDoesNotThrow(() -> companiesHouse(prefix + "000001"));
        }
        assertDoesNotThrow(() -> companiesHouse("IPABC123"));

        for (String value : new String[]{
                "ZZ000001", "UK000001", "SC00001", "SC0000001", "SC00 001",
                "SC00-001", "ＳＣ000001", "1234567A"}) {
            assertThrows(IllegalArgumentException.class, () -> companiesHouse(value));
        }
    }

    @Test
    void enforcesTheGovernedUsccDepartmentAndCategoryDirectory() {
        for (String prefix : new String[]{
                "11", "21", "31", "41", "51", "61", "71", "81",
                "91", "A1", "N1", "Y1"}) {
            assertDoesNotThrow(() -> uscc(prefix));
        }
        for (String prefix : new String[]{"22", "38", "94", "Y9"}) {
            assertThrows(IllegalArgumentException.class, () -> uscc(prefix));
        }
    }

    @Test
    void defaultStringRenderingNeverContainsTheIdentifier() {
        RegisteredIdentifier identifier = new RegisteredIdentifier(
                IdentityScope.GLOBAL_LEI_SYSTEM, RegistryScheme.LEI,
                "0000SYNTHETICFIXTU42", IdentifierStanding.CURRENT);

        assertEquals("RegisteredIdentifier[redacted]", identifier.toString());
        assertEquals("RegisteredIdentifierKey[redacted]", identifier.key().toString());
        assertFalse(identifier.toString().contains(identifier.getCanonicalValue()));
    }

    private RegisteredIdentifierKey uscc(String prefix) {
        String body = prefix + "110000F1XTURE1A";
        String alphabet = "0123456789ABCDEFGHJKLMNPQRTUWXY";
        int[] weights = {1, 3, 9, 27, 19, 26, 16, 17, 20, 29, 25, 13, 8, 24, 10, 30, 28};
        int sum = 0;
        for (int index = 0; index < body.length(); index++) {
            sum += alphabet.indexOf(body.charAt(index)) * weights[index];
        }
        String value = body + alphabet.charAt((31 - sum % 31) % 31);
        return new RegisteredIdentifierKey(
                IdentityScope.CN_UNIFIED_SOCIAL_CREDIT_CODE,
                RegistryScheme.CN_USCC, value);
    }

    private RegisteredIdentifierKey companiesHouse(String value) {
        return new RegisteredIdentifierKey(
                IdentityScope.UK_COMPANIES_HOUSE,
                RegistryScheme.GB_CH_NUMBER, value);
    }
}
