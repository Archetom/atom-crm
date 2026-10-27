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
import io.github.archetom.crm.application.port.out.enterprisedata.RegisteredAddressObservation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.application.vo.EnterpriseDataCapabilityVO;
import io.github.archetom.crm.domain.model.EnterpriseDataAttributionContext;
import io.github.archetom.crm.domain.model.EnterpriseDataSourceKind;
import io.github.archetom.crm.domain.model.EnterpriseDataUsageMode;
import io.github.archetom.crm.domain.model.EnterpriseQueryMode;
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
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnterpriseDataQueryServiceImplTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(
            7L, TENANT.getValue(), Set.of(AuthorityCatalog.ENTERPRISE_DATA_READ));
    private static final Instant NOW = Instant.parse("2026-07-19T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void searchesOnlyAfterActiveMembershipAndBeforeTheDatabaseSnapshot() {
        OrganizationAccessGuard accessGuard = accessGuard();
        EnterpriseDataSource source = mock(EnterpriseDataSource.class);
        // The manager this service actually runs on, so the boundary assertions below observe the
        // real read transaction rather than a global flag no test transaction manager ever sets.
        TestTransactionManager transactions = new TestTransactionManager();
        when(source.capabilities(TENANT)).thenAnswer(invocation -> {
            assertFalse(transactions.active());
            return capability();
        });
        when(source.searchByRegisteredName(any(), any())).thenAnswer(invocation -> {
            assertFalse(transactions.active());
            return List.of(observation());
        });
        EnterpriseDataQueryServiceImpl service = new EnterpriseDataQueryServiceImpl(
                source, policyResolver(), accessGuard,
                new QueryServiceTemplate("test-app", transactions), CLOCK);

        var capabilityResult = service.capabilities(CALLER);
        var result = service.search(CALLER, new EnterpriseNameSearchRequest()
                .setRegisteredName("Synthetic Registry")
                .setLegalJurisdiction("gb"));

        assertTrue(capabilityResult.isSuccess());
        assertEquals("EPHEMERAL_ONLY", capabilityResult.getData().usageMode());
        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().size());
        assertEquals("0000SYNTHETICFIXTU42",
                result.getData().getFirst().primaryIdentifier().canonicalValue());
        assertEquals("EPHEMERAL_ONLY", result.getData().getFirst().usageMode());
        assertEquals("SEARCH_RESULT", result.getData().getFirst().attribution().context());
        assertEquals("Controlled synthetic source",
                result.getData().getFirst().attribution().sourceName());
        assertEquals(NOW, result.getData().getFirst().observedAt());
        assertEquals(NOW.plusSeconds(300), result.getData().getFirst().expiresAt());
    }

    @Test
    void exposesCompaniesHouseIdentityThroughCapabilityAndObservationVos() {
        EnterpriseDataSource source = mock(EnterpriseDataSource.class);
        when(source.capabilities(TENANT)).thenReturn(companiesHouseCapability());
        when(source.findByRegisteredIdentifier(any(), any())).thenAnswer(invocation -> {
            var selector = invocation.getArgument(1, RegisteredIdentifierKey.class);
            assertEquals(IdentityScope.UK_COMPANIES_HOUSE, selector.getIdentityScope());
            assertEquals(RegistryScheme.GB_CH_NUMBER, selector.getRegistryScheme());
            assertEquals("SC000001", selector.getCanonicalValue());
            return Optional.of(companiesHouseObservation());
        });
        EnterpriseDataQueryServiceImpl service = service(source, accessGuard());

        var capabilityResult = service.capabilities(CALLER);
        var lookupResult = service.lookup(CALLER, new EnterpriseIdentifierLookupRequest()
                .setIdentityScope("uk_companies_house")
                .setRegistryScheme("gb_ch_number")
                .setValue(" sc000001 "));

        assertTrue(capabilityResult.isSuccess());
        assertEquals(List.of(new EnterpriseDataCapabilityVO.IdentifierTypeVO(
                        "UK_COMPANIES_HOUSE", "GB_CH_NUMBER")),
                capabilityResult.getData().identifierTypes());
        assertTrue(lookupResult.isSuccess());
        assertEquals("UK_COMPANIES_HOUSE",
                lookupResult.getData().primaryIdentifier().identityScope());
        assertEquals("GB_CH_NUMBER",
                lookupResult.getData().primaryIdentifier().registryScheme());
        assertEquals("SC000001",
                lookupResult.getData().primaryIdentifier().canonicalValue());
        assertEquals("UNKNOWN",
                lookupResult.getData().primaryIdentifier().identifierStanding());
    }

    @Test
    void rejectsInactiveCallerBeforeConsultingTheSource() {
        OrganizationAccessGuard accessGuard = mock(OrganizationAccessGuard.class);
        when(accessGuard.requireActiveMembership(CALLER, AuthorityCatalog.ENTERPRISE_DATA_READ))
                .thenThrow(new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED));
        EnterpriseDataSource source = mock(EnterpriseDataSource.class);

        var result = service(source, accessGuard).search(CALLER,
                new EnterpriseNameSearchRequest()
                        .setRegisteredName("Synthetic Registry")
                        .setLegalJurisdiction("GB"));

        assertError(result, "103");
        verify(source, never()).capabilities(any());
        verify(source, never()).searchByRegisteredName(any(), any());
    }

    @Test
    void normalizesEveryAdapterFailureAndRawRuntimeToStableSafeErrors() {
        Map<EnterpriseDataSourceError, ApplicationErrorCode> expectedErrors = Map.of(
                EnterpriseDataSourceError.NO_RESULT,
                ApplicationErrorCode.ENTERPRISE_DATA_NO_RESULT,
                EnterpriseDataSourceError.LIMITED,
                ApplicationErrorCode.ENTERPRISE_DATA_LIMITED,
                EnterpriseDataSourceError.UNAVAILABLE,
                ApplicationErrorCode.ENTERPRISE_DATA_UNAVAILABLE,
                EnterpriseDataSourceError.UNSUPPORTED,
                ApplicationErrorCode.ENTERPRISE_DATA_UNSUPPORTED);

        expectedErrors.forEach((sourceError, applicationError) -> {
            EnterpriseDataSource source = mock(EnterpriseDataSource.class);
            when(source.capabilities(TENANT)).thenReturn(capability());
            when(source.searchByRegisteredName(any(), any()))
                    .thenThrow(new EnterpriseDataSourceException(sourceError));

            var result = service(source, accessGuard()).search(CALLER,
                    new EnterpriseNameSearchRequest()
                            .setRegisteredName("Synthetic Registry")
                            .setLegalJurisdiction("GB"));

            assertError(result, applicationError);
        });

        String rawFailureCanary = "RAW_PROVIDER_FAILURE_CANARY";
        EnterpriseDataSource rawFailure = mock(EnterpriseDataSource.class);
        when(rawFailure.capabilities(TENANT)).thenReturn(capability());
        when(rawFailure.searchByRegisteredName(any(), any()))
                .thenThrow(new IllegalStateException(rawFailureCanary));

        var rawFailureResult = service(rawFailure, accessGuard()).search(CALLER,
                new EnterpriseNameSearchRequest()
                        .setRegisteredName("Synthetic Registry")
                        .setLegalJurisdiction("GB"));

        assertError(rawFailureResult, ApplicationErrorCode.ENTERPRISE_DATA_UNAVAILABLE);
        assertFalse(rawFailureResult.toString().contains(rawFailureCanary));
    }

    @Test
    void failsClosedForOverLimitAndWrongSourceBinding() {
        EnterpriseDataSource overLimit = mock(EnterpriseDataSource.class);
        when(overLimit.capabilities(TENANT)).thenReturn(capability());
        List<EnterpriseDataObservation> six = new ArrayList<>();
        for (int index = 0; index < 6; index++) six.add(observation());
        when(overLimit.searchByRegisteredName(any(), any())).thenReturn(six);
        var overLimitResult = service(overLimit, accessGuard()).search(CALLER,
                new EnterpriseNameSearchRequest()
                        .setRegisteredName("Synthetic Registry")
                        .setLegalJurisdiction("GB"));
        assertError(overLimitResult, "306");

        EnterpriseDataSource wrongSource = mock(EnterpriseDataSource.class);
        when(wrongSource.capabilities(TENANT)).thenReturn(capability());
        when(wrongSource.findByRegisteredIdentifier(any(), any()))
                .thenReturn(Optional.of(observation(
                        EnterpriseDataSourceKind.OPEN_REFERENCE_INDEX)));
        var wrongSourceResult = service(wrongSource, accessGuard())
                .lookup(CALLER, lookupRequest());
        assertError(wrongSourceResult, "306");
    }

    @Test
    void capabilityFailsClosedAndDoesNotExposeAdapterFailures() {
        EnterpriseDataSource source = mock(EnterpriseDataSource.class);
        when(source.capabilities(TENANT)).thenThrow(new IllegalStateException("private detail"));

        var result = service(source, accessGuard()).capabilities(CALLER);

        assertTrue(result.isSuccess());
        assertFalse(result.getData().ready());
        assertEquals(0, result.getData().maxCandidates());
        assertEquals("NOT_READY", result.getData().usageMode());
    }

    @Test
    void deploymentPolicyDisablesTheBoundaryBeforeConsultingTheAdapter() {
        EnterpriseDataSource source = mock(EnterpriseDataSource.class);
        EnterpriseDataReadPolicyResolver resolver = mock(EnterpriseDataReadPolicyResolver.class);
        when(resolver.resolve(TENANT)).thenReturn(EnterpriseDataReadPolicy.unavailable());

        var result = service(source, resolver, accessGuard()).search(CALLER,
                new EnterpriseNameSearchRequest()
                        .setRegisteredName("Synthetic Registry")
                        .setLegalJurisdiction("GB"));

        assertError(result, "306");
        verify(source, never()).capabilities(any());
        verify(source, never()).searchByRegisteredName(any(), any());
    }

    @Test
    void rejectsWrongJurisdictionAndUnadvertisedAdditionalIdentifiers() {
        EnterpriseDataSource wrongJurisdiction = mock(EnterpriseDataSource.class);
        when(wrongJurisdiction.capabilities(TENANT)).thenReturn(capability());
        when(wrongJurisdiction.searchByRegisteredName(any(), any()))
                .thenReturn(List.of(observation(new LegalJurisdiction("CN"))));
        var wrongJurisdictionResult = service(wrongJurisdiction, accessGuard()).search(CALLER,
                new EnterpriseNameSearchRequest()
                        .setRegisteredName("Synthetic Registry")
                        .setLegalJurisdiction("GB"));
        assertError(wrongJurisdictionResult, "306");

        RegisteredIdentifier lei = identifier();
        RegisteredIdentifier uscc = new RegisteredIdentifier(
                IdentityScope.CN_UNIFIED_SOCIAL_CREDIT_CODE, RegistryScheme.CN_USCC,
                "91110000F1XTURE1AN", IdentifierStanding.UNKNOWN);
        EnterpriseDataSource extraType = mock(EnterpriseDataSource.class);
        when(extraType.capabilities(TENANT)).thenReturn(capability());
        when(extraType.findByRegisteredIdentifier(any(), any()))
                .thenReturn(Optional.of(observation(new LegalJurisdiction("GB"),
                        lei, List.of(lei, uscc))));
        var extraTypeResult = service(extraType, accessGuard()).lookup(CALLER, lookupRequest());
        assertError(extraTypeResult, "306");
    }

    @Test
    void queryContractsRedactSearchTermsAndIdentifiers() {
        EnterpriseNameSearchRequest search = new EnterpriseNameSearchRequest()
                .setRegisteredName("Confidential Registered Name")
                .setLegalJurisdiction("GB");
        EnterpriseIdentifierLookupRequest lookup = lookupRequest();

        assertFalse(search.toString().contains("Confidential Registered Name"));
        assertFalse(lookup.toString().contains("0000SYNTHETICFIXTU42"));
        assertEquals("EnterpriseDataObservation[redacted]", observation().toString());
    }

    private EnterpriseDataQueryServiceImpl service(
            EnterpriseDataSource source, OrganizationAccessGuard accessGuard) {
        return service(source, policyResolver(), accessGuard);
    }

    private EnterpriseDataQueryServiceImpl service(
            EnterpriseDataSource source,
            EnterpriseDataReadPolicyResolver policyResolver,
            OrganizationAccessGuard accessGuard) {
        return new EnterpriseDataQueryServiceImpl(source, policyResolver, accessGuard,
                new QueryServiceTemplate("test-app", new TestTransactionManager()), CLOCK);
    }

    private EnterpriseDataReadPolicyResolver policyResolver() {
        EnterpriseDataReadPolicyResolver resolver = mock(EnterpriseDataReadPolicyResolver.class);
        when(resolver.resolve(TENANT)).thenReturn(new EnterpriseDataReadPolicy(true,
                EnterpriseDataSourceKind.SYNTHETIC_FIXTURE,
                EnterpriseDataUsageMode.ADOPT_SELECTED_FACTS,
                Duration.ofMinutes(5),
                attribution(EnterpriseDataAttributionContext.SEARCH_RESULT),
                attribution(EnterpriseDataAttributionContext.DETAIL)));
        return resolver;
    }

    private EnterpriseDataAttribution attribution(
            EnterpriseDataAttributionContext context) {
        return new EnterpriseDataAttribution(
                context, "Controlled synthetic source", null, null);
    }

    private OrganizationAccessGuard accessGuard() {
        OrganizationAccessGuard accessGuard = mock(OrganizationAccessGuard.class);
        when(accessGuard.requireActiveMembership(CALLER, AuthorityCatalog.ENTERPRISE_DATA_READ))
                .thenReturn(TENANT);
        return accessGuard;
    }

    private EnterpriseDataCapabilitySnapshot capability() {
        return new EnterpriseDataCapabilitySnapshot(true,
                Set.of(EnterpriseQueryMode.REGISTERED_NAME_SEARCH,
                        EnterpriseQueryMode.EXACT_REGISTERED_IDENTIFIER),
                Set.of(new LegalJurisdiction("GB")),
                Set.of(new RegisteredIdentifierType(
                        IdentityScope.GLOBAL_LEI_SYSTEM, RegistryScheme.LEI)),
                5);
    }

    private EnterpriseDataCapabilitySnapshot companiesHouseCapability() {
        return new EnterpriseDataCapabilitySnapshot(true,
                Set.of(EnterpriseQueryMode.REGISTERED_NAME_SEARCH,
                        EnterpriseQueryMode.EXACT_REGISTERED_IDENTIFIER),
                Set.of(new LegalJurisdiction("GB")),
                Set.of(new RegisteredIdentifierType(
                        IdentityScope.UK_COMPANIES_HOUSE,
                        RegistryScheme.GB_CH_NUMBER)),
                5);
    }

    private EnterpriseDataObservation observation() {
        return observation(EnterpriseDataSourceKind.SYNTHETIC_FIXTURE);
    }

    private EnterpriseDataObservation observation(EnterpriseDataSourceKind sourceKind) {
        RegisteredIdentifier identifier = identifier();
        return observation(new LegalJurisdiction("GB"), identifier,
                List.of(identifier), sourceKind);
    }

    private EnterpriseDataObservation companiesHouseObservation() {
        RegisteredIdentifier identifier = new RegisteredIdentifier(
                IdentityScope.UK_COMPANIES_HOUSE,
                RegistryScheme.GB_CH_NUMBER,
                "SC000001", IdentifierStanding.UNKNOWN);
        return observation(new LegalJurisdiction("GB"), identifier,
                List.of(identifier), EnterpriseDataSourceKind.SYNTHETIC_FIXTURE);
    }

    private EnterpriseDataObservation observation(LegalJurisdiction jurisdiction) {
        RegisteredIdentifier identifier = identifier();
        return observation(jurisdiction, identifier, List.of(identifier),
                EnterpriseDataSourceKind.SYNTHETIC_FIXTURE);
    }

    private EnterpriseDataObservation observation(
            LegalJurisdiction jurisdiction,
            RegisteredIdentifier primaryIdentifier,
            List<RegisteredIdentifier> identifiers) {
        return observation(jurisdiction, primaryIdentifier, identifiers,
                EnterpriseDataSourceKind.SYNTHETIC_FIXTURE);
    }

    private EnterpriseDataObservation observation(
            LegalJurisdiction jurisdiction,
            RegisteredIdentifier primaryIdentifier,
            List<RegisteredIdentifier> identifiers,
            EnterpriseDataSourceKind sourceKind) {
        return new EnterpriseDataObservation(primaryIdentifier, identifiers,
                jurisdiction, "Synthetic Registry Labs Ltd",
                RegisteredEntityStatus.ACTIVE, null, null,
                new RegisteredAddressObservation(
                        jurisdiction == null ? null : jurisdiction.value().substring(0, 2),
                        null, "Fixture City",
                        "1 Contract Test Way", null),
                sourceKind, false, NOW.minusSeconds(86_400), null);
    }

    private RegisteredIdentifier identifier() {
        return new RegisteredIdentifier(
                IdentityScope.GLOBAL_LEI_SYSTEM, RegistryScheme.LEI,
                "0000SYNTHETICFIXTU42", IdentifierStanding.CURRENT);
    }

    private EnterpriseIdentifierLookupRequest lookupRequest() {
        return new EnterpriseIdentifierLookupRequest()
                .setIdentityScope("GLOBAL_LEI_SYSTEM")
                .setRegistryScheme("LEI")
                .setValue("0000SYNTHETICFIXTU42");
    }

    private void assertError(io.github.archetom.common.result.Result<?> result, String code) {
        assertFalse(result.isSuccess());
        assertEquals(code, result.getErrorContext().fetchRootError()
                .getErrorCode().getErrorSpecific());
    }

    private void assertError(
            io.github.archetom.common.result.Result<?> result,
            ApplicationErrorCode expected) {
        assertError(result, expected.getCode());
        assertEquals(expected.getDescription(),
                result.getErrorContext().fetchRootError().getErrorMsg());
    }
}
