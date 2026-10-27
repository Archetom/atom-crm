package io.github.archetom.crm.infra.external.contract;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataCapabilitySnapshot;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataObservation;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSource;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceError;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceException;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseNameSearchQuery;
import io.github.archetom.crm.domain.model.EnterpriseDataSourceKind;
import io.github.archetom.crm.domain.model.EnterpriseQueryMode;
import io.github.archetom.crm.domain.model.FormationDateKind;
import io.github.archetom.crm.domain.model.IdentifierStanding;
import io.github.archetom.crm.domain.model.RegisteredEntityStatus;
import io.github.archetom.crm.domain.valueobject.LegalJurisdiction;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierType;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline conformance skeleton for a governed official/open enterprise-data source.
 *
 * <p>A real adapter test extends this class and backs each scenario with a stub transport and
 * synthetic provider-native payload. The contract itself must never call a live provider.</p>
 */
public abstract class OfficialSourceEnterpriseDataContract {

    protected abstract ContractDriver driver();

    @Test
    final void advertisesOnlyTheBoundedReadContract() {
        ContractDriver driver = driver();
        ContractFixture fixture = driver.fixture();
        EnterpriseDataSource source = driver.sourceFor(Scenario.READY);

        EnterpriseDataCapabilitySnapshot capability = source.capabilities(fixture.tenantId());

        assertTrue(capability.ready());
        assertEquals(Set.of(EnterpriseQueryMode.REGISTERED_NAME_SEARCH,
                        EnterpriseQueryMode.EXACT_REGISTERED_IDENTIFIER),
                capability.queryModes());
        assertTrue(capability.legalJurisdictions().contains(fixture.legalJurisdiction()));
        assertTrue(capability.identifierTypes().contains(fixture.identifierType()));
        assertTrue(capability.maxCandidates() >= 1);
        assertTrue(capability.maxCandidates()
                <= EnterpriseDataCapabilitySnapshot.CORE_MAX_CANDIDATES);
        assertThrows(IllegalArgumentException.class, () -> source.capabilities(null));
    }

    @Test
    final void exactLookupPreservesIdentityAndOrthogonalStates() {
        ContractDriver driver = driver();
        ContractFixture fixture = driver.fixture();

        EnterpriseDataObservation observation = driver.sourceFor(Scenario.READY)
                .findByRegisteredIdentifier(fixture.tenantId(), fixture.identifier())
                .orElseThrow();

        assertEquals(fixture.identifier(), observation.primaryIdentifier().key());
        assertEquals(fixture.identifierStanding(),
                observation.primaryIdentifier().getIdentifierStanding());
        assertTrue(observation.identifiers().contains(observation.primaryIdentifier()));
        assertEquals(fixture.legalJurisdiction(), observation.legalJurisdiction());
        assertEquals(fixture.registeredName(), observation.registeredName());
        assertEquals(fixture.entityStatus(), observation.entityStatus());
        assertEquals(fixture.formationDateKind(), observation.formationDateKind());
        assertEquals(fixture.formationDate(), observation.formationDate());
        assertEquals(fixture.sourceKind(), observation.sourceKind());
        assertCanonicalProjectionDoesNotLeak(observation, fixture.excludedCanary());
    }

    @Test
    final void nameSearchHonorsTheRequestedLimitAndJurisdiction() {
        ContractDriver driver = driver();
        ContractFixture fixture = driver.fixture();
        EnterpriseNameSearchQuery query = new EnterpriseNameSearchQuery(
                fixture.searchTerm(), fixture.legalJurisdiction(), 1);

        List<EnterpriseDataObservation> observations = driver.sourceFor(Scenario.READY)
                .searchByRegisteredName(fixture.tenantId(), query);

        assertFalse(observations.isEmpty());
        assertTrue(observations.size() <= query.limit());
        observations.forEach(observation -> {
            assertEquals(fixture.legalJurisdiction(), observation.legalJurisdiction());
            assertEquals(fixture.sourceKind(), observation.sourceKind());
            assertCanonicalProjectionDoesNotLeak(observation, fixture.excludedCanary());
        });
    }

    @Test
    final void unknownNativeStatesFailClosedInsteadOfGuessing() {
        ContractDriver driver = driver();
        ContractFixture fixture = driver.fixture();

        EnterpriseDataObservation observation = driver.sourceFor(Scenario.UNKNOWN_NATIVE_STATES)
                .findByRegisteredIdentifier(fixture.tenantId(), fixture.identifier())
                .orElseThrow();

        assertEquals(RegisteredEntityStatus.UNKNOWN, observation.entityStatus());
        assertEquals(IdentifierStanding.UNKNOWN,
                observation.primaryIdentifier().getIdentifierStanding());
    }

    @Test
    final void extraNativeFieldsAreDiscardedAndPartialDataRemainsExplicit() {
        ContractDriver driver = driver();
        ContractFixture fixture = driver.fixture();

        EnterpriseDataObservation observation = driver
                .sourceFor(Scenario.PARTIAL_WITH_EXCLUDED_FIELDS)
                .findByRegisteredIdentifier(fixture.tenantId(), fixture.identifier())
                .orElseThrow();

        assertTrue(observation.sourceDeclaredPartial());
        assertCanonicalProjectionDoesNotLeak(observation, fixture.excludedCanary());
    }

    @Test
    final void noResultIsRepresentedWithoutProviderDiagnostics() {
        ContractDriver driver = driver();
        ContractFixture fixture = driver.fixture();

        assertTrue(driver.sourceFor(Scenario.EXACT_NO_RESULT)
                .findByRegisteredIdentifier(fixture.tenantId(), fixture.identifier()).isEmpty());
        assertTrue(driver.sourceFor(Scenario.SEARCH_NO_RESULT)
                .searchByRegisteredName(fixture.tenantId(), new EnterpriseNameSearchQuery(
                        fixture.searchTerm(), fixture.legalJurisdiction(), 1)).isEmpty());
    }

    @Test
    final void providerFailuresCollapseToStableSafeErrors() {
        assertSafeFailure(Scenario.LIMITED, EnterpriseDataSourceError.LIMITED);
        assertSafeFailure(Scenario.UNSUPPORTED, EnterpriseDataSourceError.UNSUPPORTED);
        assertSafeFailure(Scenario.UNAVAILABLE, EnterpriseDataSourceError.UNAVAILABLE);
        assertSafeFailure(Scenario.MALFORMED_RESPONSE, EnterpriseDataSourceError.UNAVAILABLE);
        assertSafeFailure(Scenario.IDENTITY_MISMATCH, EnterpriseDataSourceError.UNAVAILABLE);
    }

    private void assertSafeFailure(Scenario scenario, EnterpriseDataSourceError expected) {
        ContractDriver driver = driver();
        ContractFixture fixture = driver.fixture();
        assertSafeException(expected, fixture,
                () -> driver.sourceFor(scenario).findByRegisteredIdentifier(
                        fixture.tenantId(), fixture.identifier()));
        assertSafeException(expected, fixture,
                () -> driver.sourceFor(scenario).searchByRegisteredName(
                        fixture.tenantId(), new EnterpriseNameSearchQuery(
                                fixture.searchTerm(), fixture.legalJurisdiction(), 1)));
    }

    private void assertSafeException(
            EnterpriseDataSourceError expected,
            ContractFixture fixture,
            Executable operation) {
        EnterpriseDataSourceException exception = assertThrows(
                EnterpriseDataSourceException.class, operation);

        assertEquals(expected, exception.error());
        assertEquals("enterprise data source operation failed", exception.getMessage());
        assertFalse(exception.toString().contains(fixture.excludedCanary()));
    }

    private void assertCanonicalProjectionDoesNotLeak(
            EnterpriseDataObservation observation, String excludedCanary) {
        assertNotNull(observation);
        String address = observation.registeredAddress() == null
                ? ""
                : String.join("|",
                        nullSafe(observation.registeredAddress().countryCode()),
                        nullSafe(observation.registeredAddress().region()),
                        nullSafe(observation.registeredAddress().locality()),
                        nullSafe(observation.registeredAddress().addressLine()),
                        nullSafe(observation.registeredAddress().postalCode()));
        String projection = String.join("|",
                observation.registeredName(),
                nullSafe(observation.sourceRevision()),
                address,
                observation.toString());
        assertFalse(projection.contains(excludedCanary));
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    public interface ContractDriver {

        ContractFixture fixture();

        EnterpriseDataSource sourceFor(Scenario scenario);
    }

    public enum Scenario {
        READY,
        UNKNOWN_NATIVE_STATES,
        PARTIAL_WITH_EXCLUDED_FIELDS,
        EXACT_NO_RESULT,
        SEARCH_NO_RESULT,
        LIMITED,
        UNSUPPORTED,
        UNAVAILABLE,
        MALFORMED_RESPONSE,
        IDENTITY_MISMATCH
    }

    public record ContractFixture(
            TenantId tenantId,
            RegisteredIdentifierKey identifier,
            RegisteredIdentifierType identifierType,
            LegalJurisdiction legalJurisdiction,
            SearchKeyword searchTerm,
            String registeredName,
            RegisteredEntityStatus entityStatus,
            IdentifierStanding identifierStanding,
            EnterpriseDataSourceKind sourceKind,
            FormationDateKind formationDateKind,
            LocalDate formationDate,
            String excludedCanary
    ) {

        public ContractFixture {
            if (tenantId == null || identifier == null || identifierType == null
                    || legalJurisdiction == null || searchTerm == null
                    || registeredName == null || registeredName.isBlank()
                    || entityStatus == null || identifierStanding == null
                    || sourceKind == null || formationDateKind == null || formationDate == null
                    || excludedCanary == null || excludedCanary.isBlank()) {
                throw new IllegalArgumentException("official source contract fixture is invalid");
            }
            if (sourceKind != EnterpriseDataSourceKind.OFFICIAL_REGISTRY
                    && sourceKind != EnterpriseDataSourceKind.OPEN_REFERENCE_INDEX) {
                throw new IllegalArgumentException(
                        "official source contract requires an official or open source kind");
            }
            if (identifier.getIdentityScope() != identifierType.identityScope()
                    || identifier.getRegistryScheme() != identifierType.registryScheme()) {
                throw new IllegalArgumentException(
                        "official source contract identifier type does not match its key");
            }
        }
    }
}
