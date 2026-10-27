package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.EnterpriseIdentifierLookupRequest;
import io.github.archetom.crm.api.dto.request.EnterpriseNameSearchRequest;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAttribution;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataCapabilitySnapshot;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataObservation;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicy;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicyResolver;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSource;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceError;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceException;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseNameSearchQuery;
import io.github.archetom.crm.application.port.out.enterprisedata.RegisteredAddressObservation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.application.vo.EnterpriseDataObservationVO;
import io.github.archetom.crm.domain.model.EnterpriseDataAttributionContext;
import io.github.archetom.crm.domain.model.EnterpriseDataSourceKind;
import io.github.archetom.crm.domain.model.EnterpriseDataUsageMode;
import io.github.archetom.crm.domain.model.EnterpriseQueryMode;
import io.github.archetom.crm.domain.model.FormationDateKind;
import io.github.archetom.crm.domain.model.IdentifierStanding;
import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegisteredEntityStatus;
import io.github.archetom.crm.domain.model.RegistryScheme;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.valueobject.LegalJurisdiction;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifier;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierType;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B23-XF/1 executable composition over two synthetic native adapter shapes.
 * The fixture covers the B23a read seam only; it does not claim that the
 * B23-XI Customer identity reservation or B23-XP adoption transaction exists.
 */
class EnterpriseDataComposedFixtureTest {

    private static final TenantId OPEN_TENANT = new TenantId(101L);
    private static final TenantId COMMERCIAL_TENANT = new TenantId(202L);
    private static final AuthenticatedCaller OPEN_CALLER = caller(11L, OPEN_TENANT);
    private static final AuthenticatedCaller COMMERCIAL_CALLER =
            caller(22L, COMMERCIAL_TENANT);
    private static final Instant NOW = Instant.parse("2026-07-20T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private ComposedFixtureAdapter adapter;
    private EnterpriseDataQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        adapter = new ComposedFixtureAdapter();
        OrganizationAccessGuard accessGuard = mock(OrganizationAccessGuard.class);
        when(accessGuard.requireActiveMembership(
                OPEN_CALLER, AuthorityCatalog.ENTERPRISE_DATA_READ))
                .thenReturn(OPEN_TENANT);
        when(accessGuard.requireActiveMembership(
                COMMERCIAL_CALLER, AuthorityCatalog.ENTERPRISE_DATA_READ))
                .thenReturn(COMMERCIAL_TENANT);
        service = new EnterpriseDataQueryServiceImpl(adapter, adapter, accessGuard,
                new QueryServiceTemplate("b23-xf", new TestTransactionManager()), CLOCK);
    }

    @Test
    void mapsGovernedIdentityAndKeepsEntityStatusOrthogonalToIdentifierStanding() {
        Result<EnterpriseDataObservationVO> openResult = service.lookup(
                OPEN_CALLER, lookup("GLOBAL_LEI_SYSTEM", "LEI", FixtureData.OPEN_LEI));
        Result<EnterpriseDataObservationVO> commercialResult = service.lookup(
                COMMERCIAL_CALLER,
                lookup("CN_UNIFIED_SOCIAL_CREDIT_CODE", "CN_USCC",
                        FixtureData.COMMERCIAL_USCC.toLowerCase()));

        assertTrue(openResult.isSuccess());
        EnterpriseDataObservationVO open = openResult.getData();
        assertEquals("GLOBAL_LEI_SYSTEM", open.primaryIdentifier().identityScope());
        assertEquals(FixtureData.OPEN_LEI, open.primaryIdentifier().canonicalValue());
        assertEquals("ACTIVE", open.entityStatus());
        assertEquals("STALE", open.primaryIdentifier().identifierStanding());
        assertEquals("FORMED", open.formationDateKind());
        assertEquals("OPEN_REFERENCE_INDEX", open.sourceKind());

        assertTrue(commercialResult.isSuccess());
        EnterpriseDataObservationVO commercial = commercialResult.getData();
        assertEquals("CN_UNIFIED_SOCIAL_CREDIT_CODE",
                commercial.primaryIdentifier().identityScope());
        assertEquals(FixtureData.COMMERCIAL_USCC,
                commercial.primaryIdentifier().canonicalValue());
        assertEquals("UNKNOWN", commercial.entityStatus());
        assertEquals("UNKNOWN", commercial.primaryIdentifier().identifierStanding());
        assertEquals("ESTABLISHED", commercial.formationDateKind());
        assertEquals("COMMERCIAL_AGGREGATOR", commercial.sourceKind());

        String allowedProjection = projectionValues(open) + projectionValues(commercial);
        assertFalse(allowedProjection.contains(FixtureData.PERSON_CANARY));
        assertFalse(allowedProjection.contains(FixtureData.CONTACT_CANARY));
        assertFalse(allowedProjection.contains(FixtureData.RELATION_CANARY));
        assertFalse(allowedProjection.contains(FixtureData.LOCATOR_CANARY));
        assertEquals(List.of("primaryIdentifier", "identifiers", "legalJurisdiction",
                        "registeredName", "entityStatus", "formationDateKind",
                        "formationDate", "registeredAddress", "sourceKind",
                        "sourceDeclaredPartial", "attribution", "observedAt",
                        "sourceUpdatedAt", "sourceRevision", "expiresAt", "usageMode"),
                Arrays.stream(EnterpriseDataObservationVO.class.getRecordComponents())
                        .map(component -> component.getName()).toList());
    }

    @Test
    void intersectsTenantPolicyWithCoreUsageTtlAttributionAndCandidateLimits() {
        var openCapability = service.capabilities(OPEN_CALLER);
        var commercialCapability = service.capabilities(COMMERCIAL_CALLER);
        var openSearch = service.search(OPEN_CALLER,
                search("Open Fixture", "GB"));
        var commercialSearch = service.search(COMMERCIAL_CALLER,
                search("商业形态合成", "CN"));

        assertTrue(openCapability.isSuccess());
        assertEquals(2, openCapability.getData().maxCandidates());
        assertEquals("EPHEMERAL_ONLY", openCapability.getData().usageMode());
        assertTrue(commercialCapability.isSuccess());
        assertEquals(1, commercialCapability.getData().maxCandidates());
        assertEquals("EPHEMERAL_ONLY", commercialCapability.getData().usageMode());

        EnterpriseDataObservationVO open = openSearch.getData().getFirst();
        assertEquals("SEARCH_RESULT", open.attribution().context());
        assertEquals("Open-model synthetic fixture", open.attribution().sourceName());
        assertEquals(NOW.plus(Duration.ofMinutes(2)), open.expiresAt());
        assertEquals(2, adapter.lastSearchLimit(OPEN_TENANT));

        EnterpriseDataObservationVO commercial = commercialSearch.getData().getFirst();
        assertEquals("SEARCH_RESULT", commercial.attribution().context());
        assertEquals("Commercial-shape synthetic fixture",
                commercial.attribution().sourceName());
        assertEquals(NOW.plusSeconds(45), commercial.expiresAt());
        assertEquals(1, adapter.lastSearchLimit(COMMERCIAL_TENANT));
    }

    @Test
    void partitionsAdmissionAndPoliciesAcrossTenantsInOneAdapterProcess() {
        adapter.setRemaining(COMMERCIAL_TENANT, 0);

        Result<List<EnterpriseDataObservationVO>> limited = service.search(
                COMMERCIAL_CALLER, search("商业形态合成", "CN"));
        Result<List<EnterpriseDataObservationVO>> open = service.search(
                OPEN_CALLER, search("Open Fixture", "GB"));

        assertError(limited, "305");
        assertTrue(open.isSuccess());
        assertEquals(1, open.getData().size());
        assertEquals(1, adapter.searchCalls(COMMERCIAL_TENANT));
        assertEquals(1, adapter.searchCalls(OPEN_TENANT));

        adapter.setPolicy(COMMERCIAL_TENANT, EnterpriseDataReadPolicy.unavailable());
        int callsBeforePolicyBlock = adapter.searchCalls(COMMERCIAL_TENANT);
        Result<List<EnterpriseDataObservationVO>> notReady = service.search(
                COMMERCIAL_CALLER, search("商业形态合成", "CN"));
        Result<List<EnterpriseDataObservationVO>> openAgain = service.search(
                OPEN_CALLER, search("Open Fixture", "GB"));

        assertError(notReady, "306");
        assertEquals(callsBeforePolicyBlock, adapter.searchCalls(COMMERCIAL_TENANT));
        assertTrue(openAgain.isSuccess());
        assertEquals("OPEN_REFERENCE_INDEX", openAgain.getData().getFirst().sourceKind());
    }

    @Test
    void rejectsCrossBindingSelectorsAndNormalizesAdapterFailuresToSafeErrors() {
        Result<EnterpriseDataObservationVO> crossBinding = service.lookup(
                COMMERCIAL_CALLER,
                lookup("GLOBAL_LEI_SYSTEM", "LEI", FixtureData.OPEN_LEI));

        assertError(crossBinding, "307");
        assertEquals(0, adapter.lookupCalls(COMMERCIAL_TENANT));

        adapter.failNext(OPEN_TENANT, EnterpriseDataSourceError.NO_RESULT);
        Result<EnterpriseDataObservationVO> missing = service.lookup(
                OPEN_CALLER, lookup("GLOBAL_LEI_SYSTEM", "LEI", FixtureData.OPEN_LEI));
        assertError(missing, "304");

        adapter.throwRawNext(OPEN_TENANT);
        Result<List<EnterpriseDataObservationVO>> failed = service.search(
                OPEN_CALLER, search("Open Fixture", "GB"));
        assertError(failed, "306");
        assertFalse(failed.toString().contains(FixtureData.RAW_ERROR_CANARY));
    }

    private static AuthenticatedCaller caller(long actorId, TenantId tenantId) {
        return new AuthenticatedCaller(actorId, tenantId.getValue(),
                Set.of(AuthorityCatalog.ENTERPRISE_DATA_READ));
    }

    private EnterpriseIdentifierLookupRequest lookup(
            String scope, String scheme, String value) {
        return new EnterpriseIdentifierLookupRequest()
                .setIdentityScope(scope)
                .setRegistryScheme(scheme)
                .setValue(value);
    }

    private EnterpriseNameSearchRequest search(String name, String jurisdiction) {
        return new EnterpriseNameSearchRequest()
                .setRegisteredName(name)
                .setLegalJurisdiction(jurisdiction);
    }

    private String projectionValues(EnterpriseDataObservationVO observation) {
        var address = observation.registeredAddress();
        return String.join("|",
                observation.registeredName(),
                observation.legalJurisdiction(),
                address == null ? "" : address.countryCode(),
                address == null || address.region() == null ? "" : address.region(),
                address == null || address.locality() == null ? "" : address.locality(),
                address == null || address.addressLine() == null ? "" : address.addressLine(),
                address == null || address.postalCode() == null ? "" : address.postalCode(),
                observation.attribution().sourceName(),
                observation.attribution().disclaimer() == null
                        ? "" : observation.attribution().disclaimer());
    }

    private void assertError(Result<?> result, String code) {
        assertFalse(result.isSuccess());
        assertEquals(code, result.getErrorContext().fetchRootError()
                .getErrorCode().getErrorSpecific());
    }

    /** Same process, but every capability, policy, observation and counter is tenant keyed. */
    private static final class ComposedFixtureAdapter
            implements EnterpriseDataSource, EnterpriseDataReadPolicyResolver {

        private final Map<TenantId, FixtureBinding> bindings = Map.of(
                OPEN_TENANT, openBinding(),
                COMMERCIAL_TENANT, commercialBinding());
        private final Map<TenantId, EnterpriseDataReadPolicy> policies = new HashMap<>();
        private final Map<TenantId, Integer> remaining = new HashMap<>();
        private final Map<TenantId, Integer> searchCalls = new HashMap<>();
        private final Map<TenantId, Integer> lookupCalls = new HashMap<>();
        private final Map<TenantId, Integer> lastSearchLimits = new HashMap<>();
        private final Map<TenantId, EnterpriseDataSourceError> nextFailures = new HashMap<>();
        private final Set<TenantId> rawFailures = new java.util.HashSet<>();

        private ComposedFixtureAdapter() {
            bindings.forEach((tenantId, binding) -> {
                policies.put(tenantId, binding.policy());
                remaining.put(tenantId, 10);
            });
        }

        @Override
        public EnterpriseDataCapabilitySnapshot capabilities(TenantId tenantId) {
            return binding(tenantId).capability();
        }

        @Override
        public EnterpriseDataReadPolicy resolve(TenantId tenantId) {
            binding(tenantId);
            return policies.getOrDefault(tenantId, EnterpriseDataReadPolicy.unavailable());
        }

        @Override
        public List<EnterpriseDataObservation> searchByRegisteredName(
                TenantId tenantId, EnterpriseNameSearchQuery query) {
            searchCalls.merge(tenantId, 1, Integer::sum);
            lastSearchLimits.put(tenantId, query.limit());
            admit(tenantId);
            FixtureBinding binding = binding(tenantId);
            EnterpriseDataObservation observation = binding.observation();
            if (!observation.legalJurisdiction().sameValueAs(query.legalJurisdiction())
                    || !observation.registeredName().toLowerCase(Locale.ROOT)
                    .contains(query.registeredName().value().toLowerCase(Locale.ROOT))) {
                return List.of();
            }
            return List.of(observation);
        }

        @Override
        public Optional<EnterpriseDataObservation> findByRegisteredIdentifier(
                TenantId tenantId, RegisteredIdentifierKey identifier) {
            lookupCalls.merge(tenantId, 1, Integer::sum);
            admit(tenantId);
            EnterpriseDataObservation observation = binding(tenantId).observation();
            return observation.primaryIdentifier().key().equals(identifier)
                    ? Optional.of(observation) : Optional.empty();
        }

        private void admit(TenantId tenantId) {
            if (rawFailures.remove(tenantId)) {
                throw new IllegalStateException(FixtureData.RAW_ERROR_CANARY);
            }
            EnterpriseDataSourceError nextFailure = nextFailures.remove(tenantId);
            if (nextFailure != null) {
                throw new EnterpriseDataSourceException(nextFailure);
            }
            int current = remaining.getOrDefault(tenantId, 0);
            if (current <= 0) {
                throw new EnterpriseDataSourceException(EnterpriseDataSourceError.LIMITED);
            }
            remaining.put(tenantId, current - 1);
        }

        private FixtureBinding binding(TenantId tenantId) {
            FixtureBinding binding = bindings.get(tenantId);
            if (binding == null) {
                throw new IllegalArgumentException("fixture tenant is not bound");
            }
            return binding;
        }

        void setRemaining(TenantId tenantId, int value) {
            binding(tenantId);
            remaining.put(tenantId, value);
        }

        void setPolicy(TenantId tenantId, EnterpriseDataReadPolicy policy) {
            binding(tenantId);
            policies.put(tenantId, policy);
        }

        void failNext(TenantId tenantId, EnterpriseDataSourceError error) {
            binding(tenantId);
            nextFailures.put(tenantId, error);
        }

        void throwRawNext(TenantId tenantId) {
            binding(tenantId);
            rawFailures.add(tenantId);
        }

        int searchCalls(TenantId tenantId) {
            return searchCalls.getOrDefault(tenantId, 0);
        }

        int lookupCalls(TenantId tenantId) {
            return lookupCalls.getOrDefault(tenantId, 0);
        }

        int lastSearchLimit(TenantId tenantId) {
            return lastSearchLimits.getOrDefault(tenantId, 0);
        }

        private static FixtureBinding openBinding() {
            OpenReferenceRawProfile raw = new OpenReferenceRawProfile(
                    FixtureData.OPEN_LEI, "ACTIVE", "LAPSED",
                    "Open Fixture Observatory Ltd", LocalDate.of(2020, 2, 3),
                    "GB", "Fixture Borough", "1 Open Test Way", "FX1 1AA",
                    FixtureData.PERSON_CANARY, FixtureData.RELATION_CANARY,
                    FixtureData.LOCATOR_CANARY);
            return new FixtureBinding(
                    capability("GB", IdentityScope.GLOBAL_LEI_SYSTEM,
                            RegistryScheme.LEI, 2),
                    policy(EnterpriseDataSourceKind.OPEN_REFERENCE_INDEX,
                            EnterpriseDataUsageMode.ADOPT_SELECTED_FACTS,
                            Duration.ofMinutes(2), "Open-model synthetic fixture"),
                    raw.toObservation());
        }

        private static FixtureBinding commercialBinding() {
            CommercialRawProfile raw = new CommercialRawProfile(
                    FixtureData.COMMERCIAL_USCC, "NATIVE_FUTURE_STATE",
                    "商业形态合成主体", LocalDate.of(2021, 4, 5),
                    "CN", "合成省", "测试市", "组合路 2 号", "000000",
                    FixtureData.PERSON_CANARY, FixtureData.CONTACT_CANARY,
                    FixtureData.LOCATOR_CANARY);
            return new FixtureBinding(
                    capability("CN", IdentityScope.CN_UNIFIED_SOCIAL_CREDIT_CODE,
                            RegistryScheme.CN_USCC, 1),
                    policy(EnterpriseDataSourceKind.COMMERCIAL_AGGREGATOR,
                            EnterpriseDataUsageMode.EPHEMERAL_ONLY,
                            Duration.ofSeconds(45),
                            "Commercial-shape synthetic fixture"),
                    raw.toObservation());
        }

        private static EnterpriseDataCapabilitySnapshot capability(
                String jurisdiction,
                IdentityScope identityScope,
                RegistryScheme registryScheme,
                int maxCandidates) {
            return new EnterpriseDataCapabilitySnapshot(true,
                    Set.of(EnterpriseQueryMode.REGISTERED_NAME_SEARCH,
                            EnterpriseQueryMode.EXACT_REGISTERED_IDENTIFIER),
                    Set.of(new LegalJurisdiction(jurisdiction)),
                    Set.of(new RegisteredIdentifierType(identityScope, registryScheme)),
                    maxCandidates);
        }

        private static EnterpriseDataReadPolicy policy(
                EnterpriseDataSourceKind sourceKind,
                EnterpriseDataUsageMode usageMode,
                Duration ttl,
                String sourceName) {
            return new EnterpriseDataReadPolicy(true, sourceKind, usageMode, ttl,
                    attribution(EnterpriseDataAttributionContext.SEARCH_RESULT, sourceName),
                    attribution(EnterpriseDataAttributionContext.DETAIL, sourceName));
        }

        private static EnterpriseDataAttribution attribution(
                EnterpriseDataAttributionContext context, String sourceName) {
            return new EnterpriseDataAttribution(context, sourceName, null,
                    "Fictional contract fixture; no production data rights are asserted.");
        }
    }

    private record FixtureBinding(
            EnterpriseDataCapabilitySnapshot capability,
            EnterpriseDataReadPolicy policy,
            EnterpriseDataObservation observation) {
    }

    /** Raw-shape-only fields are deliberately consumed or dropped before the port result. */
    private record OpenReferenceRawProfile(
            String lei,
            String entityStatus,
            String registrationStatus,
            String legalName,
            LocalDate entityCreationDate,
            String countryCode,
            String locality,
            String addressLine,
            String postalCode,
            String managingPerson,
            String relationship,
            String providerLocator) {

        EnterpriseDataObservation toObservation() {
            RegisteredIdentifier identifier = new RegisteredIdentifier(
                    IdentityScope.GLOBAL_LEI_SYSTEM, RegistryScheme.LEI, lei,
                    "LAPSED".equals(registrationStatus)
                            ? IdentifierStanding.STALE : IdentifierStanding.UNKNOWN);
            return new EnterpriseDataObservation(identifier, List.of(identifier),
                    new LegalJurisdiction(countryCode), legalName,
                    "ACTIVE".equals(entityStatus)
                            ? RegisteredEntityStatus.ACTIVE : RegisteredEntityStatus.UNKNOWN,
                    FormationDateKind.FORMED, entityCreationDate,
                    new RegisteredAddressObservation(
                            countryCode, null, locality, addressLine, postalCode),
                    EnterpriseDataSourceKind.OPEN_REFERENCE_INDEX, false,
                    NOW.minus(Duration.ofHours(2)), "open-fixture-r1");
        }
    }

    /** Unknown native status stays UNKNOWN; people/contact/locator fields are discarded. */
    private record CommercialRawProfile(
            String uscc,
            String registerStatus,
            String legalName,
            LocalDate startDate,
            String countryCode,
            String region,
            String locality,
            String addressLine,
            String postalCode,
            String legalRepresentative,
            String contact,
            String providerLocator) {

        EnterpriseDataObservation toObservation() {
            RegisteredIdentifier identifier = new RegisteredIdentifier(
                    IdentityScope.CN_UNIFIED_SOCIAL_CREDIT_CODE,
                    RegistryScheme.CN_USCC, uscc, IdentifierStanding.UNKNOWN);
            RegisteredEntityStatus status = switch (registerStatus) {
                case "ACTIVE" -> RegisteredEntityStatus.ACTIVE;
                case "INACTIVE" -> RegisteredEntityStatus.INACTIVE;
                default -> RegisteredEntityStatus.UNKNOWN;
            };
            return new EnterpriseDataObservation(identifier, List.of(identifier),
                    new LegalJurisdiction(countryCode), legalName, status,
                    FormationDateKind.ESTABLISHED, startDate,
                    new RegisteredAddressObservation(
                            countryCode, region, locality, addressLine, postalCode),
                    EnterpriseDataSourceKind.COMMERCIAL_AGGREGATOR, true,
                    null, null);
        }
    }

    private static final class FixtureData {
        static final String OPEN_LEI = "0000SYNTHETICFIXTU42";
        static final String COMMERCIAL_USCC = "91110000F1XTURE1AN";
        static final String PERSON_CANARY = "PERSON-FIELD-MUST-BE-DROPPED";
        static final String CONTACT_CANARY = "CONTACT-FIELD-MUST-BE-DROPPED";
        static final String RELATION_CANARY = "RELATION-FIELD-MUST-BE-DROPPED";
        static final String LOCATOR_CANARY = "LOCATOR-FIELD-MUST-BE-DROPPED";
        static final String RAW_ERROR_CANARY = "RAW-PROVIDER-ERROR-MUST-BE-DROPPED";

        private FixtureData() {
        }
    }
}
