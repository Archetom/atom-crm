package io.github.archetom.crm.infra.external.gleif;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataObservation;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSource;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceError;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceException;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseNameSearchQuery;
import io.github.archetom.crm.domain.model.EnterpriseDataSourceKind;
import io.github.archetom.crm.domain.model.FormationDateKind;
import io.github.archetom.crm.domain.model.IdentifierStanding;
import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegisteredEntityStatus;
import io.github.archetom.crm.domain.model.RegistryScheme;
import io.github.archetom.crm.domain.valueobject.LegalJurisdiction;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierType;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.external.contract.OfficialSourceEnterpriseDataContract;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B23-O-L1: executes B23-OC against a stub transport and synthetic GLEIF LEI payloads. */
final class GleifEnterpriseDataContractTest extends OfficialSourceEnterpriseDataContract {

    private static final TenantId TENANT = new TenantId(23_101L);
    private static final TenantId OTHER_TENANT = new TenantId(23_102L);
    private static final String LEI = "0000SYNTHETICFIXTU42";
    private static final String OTHER_LEI = "0000SYNTHETICFIXTA05";
    private static final String EXCLUDED_CANARY = "GLEIF-NATIVE-EXCLUDED-CANARY";
    private static final LegalJurisdiction JURISDICTION = new LegalJurisdiction("GB");
    private static final RegisteredIdentifierKey IDENTIFIER = new RegisteredIdentifierKey(
            IdentityScope.GLOBAL_LEI_SYSTEM, RegistryScheme.LEI, LEI);
    private static final ContractFixture FIXTURE = new ContractFixture(
            TENANT,
            IDENTIFIER,
            new RegisteredIdentifierType(IdentityScope.GLOBAL_LEI_SYSTEM, RegistryScheme.LEI),
            JURISDICTION,
            new SearchKeyword("Synthetic GLEIF Organization"),
            "Synthetic GLEIF Organization Ltd",
            RegisteredEntityStatus.ACTIVE,
            IdentifierStanding.STALE,
            EnterpriseDataSourceKind.OPEN_REFERENCE_INDEX,
            FormationDateKind.ESTABLISHED,
            LocalDate.of(2024, 3, 4),
            EXCLUDED_CANARY);

    @Override
    protected ContractDriver driver() {
        return new GleifContractDriver();
    }

    @Test
    void admitsOnlyTheFiveGovernedOrganizationCategories() {
        GleifLeiRecordMapper mapper = new GleifLeiRecordMapper();
        Set<String> admitted = Set.of(
                "GENERAL",
                "FUND",
                "BRANCH",
                "RESIDENT_GOVERNMENT_ENTITY",
                "INTERNATIONAL_ORGANIZATION");

        admitted.forEach(category -> {
            assertTrue(mapper.isQualifiedCategory(category));
            assertEquals(FIXTURE.registeredName(), sourceReturning(page(record(
                    category, "ACTIVE", "ISSUED", LEI, "GB", false)))
                    .findByRegisteredIdentifier(TENANT, IDENTIFIER)
                    .orElseThrow()
                    .registeredName());
        });

        for (String rejected : new String[]{"SOLE_PROPRIETOR", "FUTURE_CATEGORY", ""}) {
            assertFalse(mapper.isQualifiedCategory(rejected));
            EnterpriseDataSourceException exception = assertThrows(
                    EnterpriseDataSourceException.class,
                    () -> sourceReturning(page(record(
                            rejected, "ACTIVE", "ISSUED", LEI, "GB", false)))
                            .findByRegisteredIdentifier(TENANT, IDENTIFIER));
            assertEquals(EnterpriseDataSourceError.UNSUPPORTED, exception.error());
            assertEquals("enterprise data source operation failed", exception.getMessage());
        }
        assertFalse(mapper.isQualifiedCategory(null));

        EnterpriseDataSourceException missingCategory = assertThrows(
                EnterpriseDataSourceException.class,
                () -> sourceReturning(page(record(
                        null, "ACTIVE", "ISSUED", LEI, "GB", false)))
                        .findByRegisteredIdentifier(TENANT, IDENTIFIER));
        assertEquals(EnterpriseDataSourceError.UNSUPPORTED, missingCategory.error());
    }

    @Test
    void searchDropsUnqualifiedRecordsBeforeReturningProfileValues() {
        GleifLeiPage page = page(
                record("SOLE_PROPRIETOR", "ACTIVE", "ISSUED", LEI, "GB", false),
                record(null, "ACTIVE", "ISSUED", LEI, "GB", false),
                record("FUTURE_CATEGORY", "ACTIVE", "ISSUED", LEI, "GB", false),
                record("GENERAL", "ACTIVE", "ISSUED", LEI, "GB", false));
        EnterpriseDataSource source = sourceReturning(page);

        List<EnterpriseDataObservation> observations = source.searchByRegisteredName(
                TENANT, new EnterpriseNameSearchQuery(
                        FIXTURE.searchTerm(), JURISDICTION, 4));

        assertEquals(1, observations.size());
        assertEquals(FIXTURE.registeredName(), observations.getFirst().registeredName());
        assertFalse(observations.toString().contains(EXCLUDED_CANARY));
    }

    @Test
    void mapsEntityAndRegistrationStateOnIndependentAxes() {
        Map<String, IdentifierStanding> standings = new LinkedHashMap<>();
        standings.put("ISSUED", IdentifierStanding.CURRENT);
        standings.put("LAPSED", IdentifierStanding.STALE);
        standings.put("PENDING_TRANSFER", IdentifierStanding.TRANSITIONING);
        standings.put("PENDING_ARCHIVAL", IdentifierStanding.TRANSITIONING);
        standings.put("DUPLICATE", IdentifierStanding.SUPERSEDED);
        standings.put("ANNULLED", IdentifierStanding.INVALID);
        standings.put("RETIRED", IdentifierStanding.RETIRED);
        standings.put("PENDING_VALIDATION", IdentifierStanding.UNKNOWN);
        standings.put("FUTURE_REGISTRATION_STATUS", IdentifierStanding.UNKNOWN);

        standings.forEach((nativeStatus, expectedStanding) -> {
            EnterpriseDataObservation observation = sourceReturning(page(record(
                    "GENERAL", "ACTIVE", nativeStatus, LEI, "GB", false)))
                    .findByRegisteredIdentifier(TENANT, IDENTIFIER)
                    .orElseThrow();
            assertEquals(RegisteredEntityStatus.ACTIVE, observation.entityStatus());
            assertEquals(expectedStanding,
                    observation.primaryIdentifier().getIdentifierStanding());
        });

        EnterpriseDataObservation inactive = sourceReturning(page(record(
                "GENERAL", "INACTIVE", "ISSUED", LEI, "GB", false)))
                .findByRegisteredIdentifier(TENANT, IDENTIFIER)
                .orElseThrow();
        assertEquals(RegisteredEntityStatus.INACTIVE, inactive.entityStatus());
        assertEquals(IdentifierStanding.CURRENT,
                inactive.primaryIdentifier().getIdentifierStanding());
    }

    @Test
    void mapsLastUpdateAndGovernedRaIdentityButDoesNotPromoteRenewalFields() {
        EnterpriseDataObservation observation = sourceReturning(page(record(
                "GENERAL", "ACTIVE", "LAPSED", LEI, "GB", false)))
                .findByRegisteredIdentifier(TENANT, IDENTIFIER)
                .orElseThrow();

        assertTrue(sourceReturning(page(record(
                        "GENERAL", "ACTIVE", "LAPSED", LEI, "GB", false)))
                .capabilities(TENANT)
                .identifierTypes()
                .contains(new RegisteredIdentifierType(
                        IdentityScope.UK_COMPANIES_HOUSE,
                        RegistryScheme.GB_CH_NUMBER)));
        assertEquals(Instant.parse("2026-07-19T10:11:12Z"), observation.sourceUpdatedAt());
        assertNull(observation.sourceRevision());
        assertEquals(2, observation.identifiers().size());
        assertEquals("00000001", observation.identifiers().get(1).getCanonicalValue());
        assertEquals(IdentityScope.UK_COMPANIES_HOUSE,
                observation.identifiers().get(1).getIdentityScope());
        assertEquals(RegistryScheme.GB_CH_NUMBER,
                observation.identifiers().get(1).getRegistryScheme());
        assertEquals(IdentifierStanding.UNKNOWN,
                observation.identifiers().get(1).getIdentifierStanding());
        assertEquals(IdentityScope.GLOBAL_LEI_SYSTEM,
                observation.primaryIdentifier().getIdentityScope());
        assertEquals(RegistryScheme.LEI,
                observation.primaryIdentifier().getRegistryScheme());
    }

    @Test
    void unknownRaWrongNumberClassAndMissingEntityIdOmitOnlyTheSecondaryIdentity() {
        for (GleifLeiRecord record : List.of(
                record("GENERAL", "ACTIVE", "ISSUED", LEI, "GB", false,
                        "RA999999", "00000001"),
                record("GENERAL", "ACTIVE", "ISSUED", LEI, "GB", false,
                        "RA000585", "SC000001"),
                record("GENERAL", "ACTIVE", "ISSUED", LEI, "GB", false,
                        "RA000587", null))) {
            EnterpriseDataObservation observation = sourceReturning(page(record))
                    .findByRegisteredIdentifier(TENANT, IDENTIFIER)
                    .orElseThrow();

            assertEquals(List.of(observation.primaryIdentifier()), observation.identifiers());
        }
    }

    @Test
    void primaryIdentityMismatchStillFailsWhenTheRaIdentityIsValid() {
        EnterpriseDataSourceException exception = assertThrows(
                EnterpriseDataSourceException.class,
                () -> sourceReturning(page(record(
                        "GENERAL", "ACTIVE", "ISSUED", OTHER_LEI, "GB", false,
                        "RA000585", "00000001")))
                        .findByRegisteredIdentifier(TENANT, IDENTIFIER));

        assertEquals(EnterpriseDataSourceError.UNAVAILABLE, exception.error());
        assertEquals("enterprise data source operation failed", exception.getMessage());
    }

    @Test
    void sendsOneBoundedTypedCallToTheStubTransport() {
        StubGleifTransport transport = new StubGleifTransport(Scenario.READY);
        EnterpriseDataSource source = source(transport);

        source.findByRegisteredIdentifier(TENANT, IDENTIFIER).orElseThrow();
        source.searchByRegisteredName(TENANT, new EnterpriseNameSearchQuery(
                FIXTURE.searchTerm(), JURISDICTION, 2));

        assertEquals(1, transport.exactCalls);
        assertEquals(1, transport.searchCalls);
        assertEquals(LEI, transport.exactSelector);
        assertEquals(FIXTURE.searchTerm().value(), transport.searchQuery.registeredName());
        assertEquals(JURISDICTION.value(), transport.searchQuery.legalJurisdiction());
        assertEquals(2, transport.searchQuery.pageSize());
    }

    @Test
    void crossTenantAndUnexpectedFailuresStaySafeAndDoNotCallTheTransport() {
        StubGleifTransport transport = new StubGleifTransport(Scenario.READY);
        EnterpriseDataSource source = source(transport);

        assertFalse(source.capabilities(OTHER_TENANT).ready());
        EnterpriseDataSourceException crossTenant = assertThrows(
                EnterpriseDataSourceException.class,
                () -> source.findByRegisteredIdentifier(OTHER_TENANT, IDENTIFIER));
        assertEquals(EnterpriseDataSourceError.UNAVAILABLE, crossTenant.error());
        assertEquals(0, transport.exactCalls);

        EnterpriseDataSource unsafeTransport = source(new GleifTransport() {
            @Override
            public GleifLeiPage findByLei(TenantId tenantId, String lei) {
                throw new IllegalStateException(EXCLUDED_CANARY);
            }

            @Override
            public GleifLeiPage searchByLegalName(
                    TenantId tenantId, GleifLegalNameQuery query) {
                throw new IllegalStateException(EXCLUDED_CANARY);
            }
        });
        EnterpriseDataSourceException collapsed = assertThrows(
                EnterpriseDataSourceException.class,
                () -> unsafeTransport.findByRegisteredIdentifier(TENANT, IDENTIFIER));
        assertEquals(EnterpriseDataSourceError.UNAVAILABLE, collapsed.error());
        assertFalse(collapsed.toString().contains(EXCLUDED_CANARY));
    }

    private EnterpriseDataSource sourceReturning(GleifLeiPage page) {
        return source(new FixedGleifTransport(page));
    }

    private EnterpriseDataSource source(GleifTransport transport) {
        return new GleifEnterpriseDataSource(TENANT, Set.of(JURISDICTION), transport);
    }

    private static GleifLeiPage page(GleifLeiRecord... records) {
        return new GleifLeiPage("1.0", List.of(records));
    }

    private static GleifLeiRecord record(
            String category,
            String entityStatus,
            String registrationStatus,
            String lei,
            String jurisdiction,
            boolean partial) {
        return record(category, entityStatus, registrationStatus, lei, jurisdiction, partial,
                "RA000585", "00000001");
    }

    private static GleifLeiRecord record(
            String category,
            String entityStatus,
            String registrationStatus,
            String lei,
            String jurisdiction,
            boolean partial,
            String registrationAuthorityId,
            String registrationAuthorityEntityId) {
        return new GleifLeiRecord(
                "lei-records",
                lei,
                new GleifEntity(
                        FIXTURE.registeredName(),
                        jurisdiction,
                        category,
                        entityStatus,
                        FIXTURE.formationDate(),
                        new GleifAddress(
                                List.of("1 Synthetic Registry Way", "Fixture Quarter"),
                                "Fixture City",
                                "Fixture Region",
                                "GB",
                                "FX1 1AA"),
                        registrationAuthorityId,
                        registrationAuthorityEntityId),
                new GleifRegistration(
                        registrationStatus,
                        Instant.parse("2026-07-19T10:11:12Z"),
                        Instant.parse("2027-07-19T10:11:12Z"),
                        "FULLY_CORROBORATED"),
                partial,
                new GleifExcludedNativeFields(
                        EXCLUDED_CANARY,
                        EXCLUDED_CANARY,
                        EXCLUDED_CANARY,
                        EXCLUDED_CANARY,
                        EXCLUDED_CANARY));
    }

    private static final class GleifContractDriver implements ContractDriver {

        @Override
        public ContractFixture fixture() {
            return FIXTURE;
        }

        @Override
        public EnterpriseDataSource sourceFor(Scenario scenario) {
            return new GleifEnterpriseDataSource(
                    TENANT, Set.of(JURISDICTION), new StubGleifTransport(scenario));
        }
    }

    private static final class FixedGleifTransport implements GleifTransport {

        private final GleifLeiPage page;

        private FixedGleifTransport(GleifLeiPage page) {
            this.page = page;
        }

        @Override
        public GleifLeiPage findByLei(TenantId tenantId, String lei) {
            return page;
        }

        @Override
        public GleifLeiPage searchByLegalName(
                TenantId tenantId, GleifLegalNameQuery query) {
            return page;
        }
    }

    private static final class StubGleifTransport implements GleifTransport {

        private final Scenario scenario;
        private int exactCalls;
        private int searchCalls;
        private String exactSelector;
        private GleifLegalNameQuery searchQuery;

        private StubGleifTransport(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public GleifLeiPage findByLei(TenantId tenantId, String lei) {
            exactCalls++;
            exactSelector = lei;
            failIfRequired();
            if (scenario == Scenario.EXACT_NO_RESULT) {
                return new GleifLeiPage("1.0", List.of());
            }
            if (scenario == Scenario.MALFORMED_RESPONSE) {
                return new GleifLeiPage("future-json-api", List.of());
            }
            return new GleifLeiPage("1.0", List.of(scenarioRecord(false)));
        }

        @Override
        public GleifLeiPage searchByLegalName(
                TenantId tenantId, GleifLegalNameQuery query) {
            searchCalls++;
            searchQuery = query;
            failIfRequired();
            if (scenario == Scenario.SEARCH_NO_RESULT) {
                return new GleifLeiPage("1.0", List.of());
            }
            if (scenario == Scenario.MALFORMED_RESPONSE) {
                return new GleifLeiPage("future-json-api", List.of());
            }
            return new GleifLeiPage("1.0", List.of(scenarioRecord(true)));
        }

        private GleifLeiRecord scenarioRecord(boolean search) {
            return switch (scenario) {
                case UNKNOWN_NATIVE_STATES -> record(
                        "GENERAL", "FUTURE_ENTITY_STATUS", "FUTURE_REGISTRATION_STATUS",
                        LEI, "GB", false);
                case PARTIAL_WITH_EXCLUDED_FIELDS -> record(
                        "GENERAL", "ACTIVE", "LAPSED", LEI, "GB", true);
                case IDENTITY_MISMATCH -> record(
                        "GENERAL", "ACTIVE", "LAPSED", search ? LEI : OTHER_LEI,
                        search ? "ZZ" : "GB", false);
                default -> record("GENERAL", "ACTIVE", "LAPSED", LEI, "GB", false);
            };
        }

        private void failIfRequired() {
            GleifTransportError error = switch (scenario) {
                case LIMITED -> GleifTransportError.LIMITED;
                case UNSUPPORTED -> GleifTransportError.UNSUPPORTED;
                case UNAVAILABLE -> GleifTransportError.UNAVAILABLE;
                default -> null;
            };
            if (error != null) {
                throw new GleifTransportException(error);
            }
        }
    }
}
