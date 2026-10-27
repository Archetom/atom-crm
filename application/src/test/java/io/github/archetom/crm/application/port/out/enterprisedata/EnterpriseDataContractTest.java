package io.github.archetom.crm.application.port.out.enterprisedata;

import io.github.archetom.crm.domain.model.EnterpriseDataSourceKind;
import io.github.archetom.crm.domain.model.EnterpriseDataUsageMode;
import io.github.archetom.crm.domain.model.EnterpriseQueryMode;
import io.github.archetom.crm.domain.model.FormationDateKind;
import io.github.archetom.crm.domain.model.IdentifierStanding;
import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegisteredEntityStatus;
import io.github.archetom.crm.domain.model.RegistryScheme;
import io.github.archetom.crm.domain.valueobject.LegalJurisdiction;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifier;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnterpriseDataContractTest {

    private static final Instant SOURCE_UPDATED_AT = Instant.parse("2026-07-20T00:00:00Z");

    @Test
    void freezesTheB23XpDateAndSourceKindVocabularies() {
        assertEquals(List.of("INCORPORATED", "REGISTERED", "FORMED", "ESTABLISHED", "OPENED"),
                List.of(FormationDateKind.values()).stream().map(Enum::name).toList());
        assertEquals(List.of("OFFICIAL_REGISTRY", "OPEN_REFERENCE_INDEX",
                        "COMMERCIAL_AGGREGATOR", "SYNTHETIC_FIXTURE"),
                List.of(EnterpriseDataSourceKind.values()).stream().map(Enum::name).toList());
    }

    @Test
    void freezesTheRegisteredIdentityApiVocabulary() {
        assertEquals(List.of("CN_UNIFIED_SOCIAL_CREDIT_CODE", "GLOBAL_LEI_SYSTEM",
                        "UK_COMPANIES_HOUSE"),
                List.of(IdentityScope.values()).stream().map(Enum::name).toList());
        assertEquals(List.of("CN_USCC", "LEI", "GB_CH_NUMBER"),
                List.of(RegistryScheme.values()).stream().map(Enum::name).toList());

        assertDoesNotThrow(() -> new EnterpriseDataCapabilitySnapshot(true,
                Set.of(EnterpriseQueryMode.EXACT_REGISTERED_IDENTIFIER), Set.of(),
                Set.of(new RegisteredIdentifierType(
                        IdentityScope.UK_COMPANIES_HOUSE,
                        RegistryScheme.GB_CH_NUMBER)), 1));
    }

    @Test
    void nameSearchCapabilityRequiresAtLeastOneIdentifierType() {
        assertThrows(IllegalArgumentException.class, () ->
                new EnterpriseDataCapabilitySnapshot(true,
                        Set.of(EnterpriseQueryMode.REGISTERED_NAME_SEARCH),
                        Set.of(new LegalJurisdiction("GB")), Set.of(), 5));
    }

    @Test
    void primaryIdentifierMustBePresentWithTheSameStanding() {
        RegisteredIdentifier current = identifier(IdentifierStanding.CURRENT);
        RegisteredIdentifier stale = identifier(IdentifierStanding.STALE);

        assertThrows(IllegalArgumentException.class, () -> observation(
                current, List.of(stale), "Synthetic Registry Labs Ltd"));
    }

    @Test
    void legalJurisdictionIsOptionalButRegisteredNameUsesCustomerLimit() {
        RegisteredIdentifier current = identifier(IdentifierStanding.CURRENT);

        assertDoesNotThrow(() -> observation(
                current, List.of(current), "Synthetic Registry Labs Ltd"));
        assertThrows(IllegalArgumentException.class, () -> observation(
                current, List.of(current), "X".repeat(201)));
    }

    @Test
    void unavailableReadPolicyCannotAdvertiseAdoption() {
        assertThrows(IllegalArgumentException.class, () ->
                new EnterpriseDataReadPolicy(false,
                        EnterpriseDataSourceKind.SYNTHETIC_FIXTURE,
                        EnterpriseDataUsageMode.ADOPT_SELECTED_FACTS,
                        EnterpriseDataReadPolicy.CORE_MAX_OBSERVATION_TTL, null, null));
    }

    private EnterpriseDataObservation observation(
            RegisteredIdentifier primary,
            List<RegisteredIdentifier> identifiers,
            String registeredName) {
        return new EnterpriseDataObservation(primary, identifiers, null, registeredName,
                RegisteredEntityStatus.ACTIVE, null, null, null,
                EnterpriseDataSourceKind.SYNTHETIC_FIXTURE, false, SOURCE_UPDATED_AT,
                null);
    }

    private RegisteredIdentifier identifier(IdentifierStanding standing) {
        return new RegisteredIdentifier(IdentityScope.GLOBAL_LEI_SYSTEM,
                RegistryScheme.LEI, "0000SYNTHETICFIXTU42", standing);
    }
}
