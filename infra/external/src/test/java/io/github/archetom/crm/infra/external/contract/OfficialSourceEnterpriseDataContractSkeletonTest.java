package io.github.archetom.crm.infra.external.contract;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataCapabilitySnapshot;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataObservation;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSource;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceError;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceException;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseNameSearchQuery;
import io.github.archetom.crm.application.port.out.enterprisedata.RegisteredAddressObservation;
import io.github.archetom.crm.domain.model.EnterpriseDataSourceKind;
import io.github.archetom.crm.domain.model.EnterpriseQueryMode;
import io.github.archetom.crm.domain.model.FormationDateKind;
import io.github.archetom.crm.domain.model.IdentifierStanding;
import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegisteredEntityStatus;
import io.github.archetom.crm.domain.model.RegistryScheme;
import io.github.archetom.crm.domain.valueobject.LegalJurisdiction;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifier;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierType;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Executes the reusable contract against an in-memory, synthetic native-shape driver only. */
final class OfficialSourceEnterpriseDataContractSkeletonTest
        extends OfficialSourceEnterpriseDataContract {

    private static final TenantId TENANT = new TenantId(23_001L);
    private static final String LEI = "0000SYNTHETICFIXTU42";
    private static final String EXCLUDED_CANARY = "NATIVE-OFFICIAL-SECRET-CANARY";
    private static final LegalJurisdiction JURISDICTION = new LegalJurisdiction("GB");
    private static final RegisteredIdentifierKey IDENTIFIER = new RegisteredIdentifierKey(
            IdentityScope.GLOBAL_LEI_SYSTEM, RegistryScheme.LEI, LEI);
    private static final ContractFixture FIXTURE = new ContractFixture(
            TENANT,
            IDENTIFIER,
            new RegisteredIdentifierType(IdentityScope.GLOBAL_LEI_SYSTEM, RegistryScheme.LEI),
            JURISDICTION,
            new SearchKeyword("Official Shape Fixture"),
            "Official Shape Fixture Ltd",
            RegisteredEntityStatus.ACTIVE,
            IdentifierStanding.STALE,
            EnterpriseDataSourceKind.OPEN_REFERENCE_INDEX,
            FormationDateKind.ESTABLISHED,
            LocalDate.of(2024, 2, 3),
            EXCLUDED_CANARY);

    @Override
    protected ContractDriver driver() {
        return new OfflineContractDriver();
    }

    private static final class OfflineContractDriver implements ContractDriver {

        @Override
        public ContractFixture fixture() {
            return FIXTURE;
        }

        @Override
        public EnterpriseDataSource sourceFor(Scenario scenario) {
            return new OfflineOfficialShapeSource(scenario);
        }
    }

    /**
     * Reference driver for the test harness. Its only input is code-owned synthetic data; it has
     * no HTTP transport, endpoint, credential, Spring annotation, or production registration.
     */
    private static final class OfflineOfficialShapeSource implements EnterpriseDataSource {

        private final Scenario scenario;

        private OfflineOfficialShapeSource(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public EnterpriseDataCapabilitySnapshot capabilities(TenantId tenantId) {
            requireTenant(tenantId);
            return new EnterpriseDataCapabilitySnapshot(true,
                    Set.of(EnterpriseQueryMode.REGISTERED_NAME_SEARCH,
                            EnterpriseQueryMode.EXACT_REGISTERED_IDENTIFIER),
                    Set.of(JURISDICTION),
                    Set.of(FIXTURE.identifierType()),
                    2);
        }

        @Override
        public List<EnterpriseDataObservation> searchByRegisteredName(
                TenantId tenantId, EnterpriseNameSearchQuery query) {
            requireTenant(tenantId);
            if (query == null) {
                throw new IllegalArgumentException("query must not be null");
            }
            if (scenario == Scenario.SEARCH_NO_RESULT) {
                return List.of();
            }
            failIfRequired();
            if (!JURISDICTION.sameValueAs(query.legalJurisdiction())) {
                return List.of();
            }
            return List.of(observation(scenario)).stream().limit(query.limit()).toList();
        }

        @Override
        public Optional<EnterpriseDataObservation> findByRegisteredIdentifier(
                TenantId tenantId, RegisteredIdentifierKey identifier) {
            requireTenant(tenantId);
            if (identifier == null) {
                throw new IllegalArgumentException("identifier must not be null");
            }
            if (scenario == Scenario.EXACT_NO_RESULT) {
                return Optional.empty();
            }
            failIfRequired();
            if (!IDENTIFIER.equals(identifier)) {
                return Optional.empty();
            }
            return Optional.of(observation(scenario));
        }

        private EnterpriseDataObservation observation(Scenario selected) {
            OfflineNativeRecord nativeRecord = nativeRecord(selected);
            RegisteredIdentifier identifier = new RegisteredIdentifier(
                    IDENTIFIER.getIdentityScope(), IDENTIFIER.getRegistryScheme(),
                    nativeRecord.lei(), identifierStanding(nativeRecord.registrationStatus()));
            return new EnterpriseDataObservation(
                    identifier,
                    List.of(identifier),
                    new LegalJurisdiction(nativeRecord.legalJurisdiction()),
                    nativeRecord.legalName(),
                    entityStatus(nativeRecord.entityStatus()),
                    FIXTURE.formationDateKind(),
                    nativeRecord.creationDate(),
                    new RegisteredAddressObservation(
                            nativeRecord.address().countryCode(), null,
                            nativeRecord.address().locality(),
                            nativeRecord.address().addressLine(),
                            nativeRecord.address().postalCode()),
                    FIXTURE.sourceKind(),
                    nativeRecord.partial(),
                    Instant.parse("2026-07-20T00:00:00Z"),
                    "offline-revision-1");
        }

        private OfflineNativeRecord nativeRecord(Scenario selected) {
            boolean unknown = selected == Scenario.UNKNOWN_NATIVE_STATES;
            return new OfflineNativeRecord(
                    LEI,
                    FIXTURE.registeredName(),
                    JURISDICTION.value(),
                    unknown ? "FUTURE_ENTITY_STATE" : "ACTIVE",
                    unknown ? "FUTURE_REGISTRATION_STATE" : "LAPSED",
                    FIXTURE.formationDate(),
                    new OfflineNativeAddress(
                            "GB", "Fixture City", "1 Offline Contract Way", "FX1 1AA"),
                    selected == Scenario.PARTIAL_WITH_EXCLUDED_FIELDS,
                    EXCLUDED_CANARY,
                    EXCLUDED_CANARY,
                    EXCLUDED_CANARY);
        }

        private RegisteredEntityStatus entityStatus(String nativeStatus) {
            return switch (nativeStatus) {
                case "ACTIVE" -> RegisteredEntityStatus.ACTIVE;
                case "INACTIVE" -> RegisteredEntityStatus.INACTIVE;
                default -> RegisteredEntityStatus.UNKNOWN;
            };
        }

        private IdentifierStanding identifierStanding(String nativeStatus) {
            return switch (nativeStatus) {
                case "ISSUED" -> IdentifierStanding.CURRENT;
                case "LAPSED" -> IdentifierStanding.STALE;
                default -> IdentifierStanding.UNKNOWN;
            };
        }

        private void failIfRequired() {
            EnterpriseDataSourceError error = switch (scenario) {
                case LIMITED -> EnterpriseDataSourceError.LIMITED;
                case UNSUPPORTED -> EnterpriseDataSourceError.UNSUPPORTED;
                case UNAVAILABLE, MALFORMED_RESPONSE, IDENTITY_MISMATCH ->
                        EnterpriseDataSourceError.UNAVAILABLE;
                default -> null;
            };
            if (error != null) {
                throw new EnterpriseDataSourceException(error);
            }
        }

        private void requireTenant(TenantId tenantId) {
            if (tenantId == null) {
                throw new IllegalArgumentException("tenant must not be null");
            }
        }

        /** Fields after {@code partial} are deliberate adapter-boundary exclusion canaries. */
        private record OfflineNativeRecord(
                String lei,
                String legalName,
                String legalJurisdiction,
                String entityStatus,
                String registrationStatus,
                LocalDate creationDate,
                OfflineNativeAddress address,
                boolean partial,
                String personName,
                String providerLocator,
                String futureExtraField
        ) {
        }

        private record OfflineNativeAddress(
                String countryCode,
                String locality,
                String addressLine,
                String postalCode
        ) {
        }
    }
}
