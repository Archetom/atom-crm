package io.github.archetom.crm.infra.external.gleif.runtime;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceError;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceException;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseNameSearchQuery;
import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegistryScheme;
import io.github.archetom.crm.domain.valueobject.LegalJurisdiction;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GleifEnterpriseDataSourceRuntimeTest {
    private static final TenantId TENANT = new TenantId(91L);
    private static final RegisteredIdentifierKey LEI = new RegisteredIdentifierKey(
            IdentityScope.GLOBAL_LEI_SYSTEM, RegistryScheme.LEI, "00000000000000000001");
    private static final RegisteredIdentifierKey COMPANIES_HOUSE = new RegisteredIdentifierKey(
            IdentityScope.UK_COMPANIES_HOUSE, RegistryScheme.GB_CH_NUMBER, "00000001");

    @Test
    void mapsOnlyAllowlistedLevelOneFactsAndThePinnedRaIdentity() {
        RecordingQuota quota = new RecordingQuota(true, true);
        GleifEnterpriseDataSource source = source(quota, new FixedTransport(200, envelope(record())));

        var result = source.findByRegisteredIdentifier(TENANT, LEI).orElseThrow();

        assertEquals(LEI, result.primaryIdentifier().key());
        assertEquals(List.of(LEI, COMPANIES_HOUSE), result.identifiers().stream().map(value -> value.key()).toList());
        assertEquals("Synthetic GLEIF Registry Ltd", result.registeredName());
        assertTrue(result.sourceDeclaredPartial());
        assertFalse(result.toString().contains("excluded-person-canary"));
        assertEquals(1, quota.calls);
    }

    @Test
    void resolvesARegisteredAsSelectorOnlyWhenThePinnedRaCatalogConfirmsIt() {
        GleifEnterpriseDataSource source = source(new RecordingQuota(true, true),
                new FixedTransport(200, envelopeArray(record())));

        assertEquals(LEI, source.findByRegisteredIdentifier(TENANT, COMPANIES_HOUSE)
                .orElseThrow().primaryIdentifier().key());
    }

    @Test
    void keepsPolicyAndQuotaAsReadinessGatesInsteadOfTreatingEnablementAsReadiness() {
        GleifEnterpriseDataSource noQuota = source(new RecordingQuota(false, false),
                new FixedTransport(200, envelope(record())));
        assertFalse(noQuota.capabilities(TENANT).ready());
        EnterpriseDataSourceException unavailable = assertThrows(EnterpriseDataSourceException.class,
                () -> noQuota.searchByRegisteredName(TENANT,
                        new EnterpriseNameSearchQuery(new SearchKeyword("Synthetic"),
                                new LegalJurisdiction("GB"), 1)));
        assertEquals(EnterpriseDataSourceError.UNAVAILABLE, unavailable.error());

        GleifEnterpriseDataProperties properties = properties();
        properties.setPolicyDigest("incorrect");
        GleifEnterpriseDataSource badPolicy = new GleifEnterpriseDataSource(properties,
                new RecordingQuota(true, true), new FixedTransport(200, envelope(record())),
                new ObjectMapper(), Clock.systemUTC());
        assertFalse(badPolicy.capabilities(TENANT).ready());
    }

    @Test
    void mapsRateLimitingWithoutExposingNativeResponseDetail() {
        GleifEnterpriseDataSource source = source(new RecordingQuota(true, true),
                new FixedTransport(429, "{\"errors\":[\"excluded-person-canary\"]}"));

        EnterpriseDataSourceException exception = assertThrows(EnterpriseDataSourceException.class,
                () -> source.findByRegisteredIdentifier(TENANT, LEI));

        assertEquals(EnterpriseDataSourceError.LIMITED, exception.error());
        assertFalse(exception.toString().contains("excluded-person-canary"));
    }

    @Test
    void rejectsTheValueFreePartialLiveCanaryAsProductionReadiness() {
        GleifConformanceReportSource partial = () -> Optional.of(new GleifConformanceReportSource.Report(
                "B23_GLEIF_LIVE_CANARY_V1", "CANARY_LIVE_ONLY", GleifSourcePolicyCatalog.DIGEST,
                GleifRegistrationAuthorityCatalog.DIGEST, "CANARY_LOCAL_ONLY",
                Instant.parse("2026-07-23T00:05:00Z"), Set.of("O2-12")));
        GleifEnterpriseDataSource source = new GleifEnterpriseDataSource(properties(),
                new RecordingQuota(true, true), new FixedTransport(200, envelope(record())), new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC), partial, false);

        assertFalse(source.capabilities(TENANT).ready());
    }

    private static GleifEnterpriseDataSource source(RecordingQuota quota, GleifHttpTransport transport) {
        return new GleifEnterpriseDataSource(properties(), quota, transport, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC));
    }

    private static GleifEnterpriseDataProperties properties() {
        GleifEnterpriseDataProperties properties = new GleifEnterpriseDataProperties();
        properties.setEnabled(true);
        properties.setApiBaseUri(URI.create("https://api.gleif.org/api/v1/"));
        properties.setRequestTimeout(Duration.ofSeconds(3));
        properties.setMaxCandidates(5);
        properties.setMaximumObservationTtl(Duration.ofMinutes(2));
        properties.setPolicyVersion(GleifSourcePolicyCatalog.VERSION);
        properties.setPolicyDigest(GleifSourcePolicyCatalog.DIGEST);
        properties.setRaCatalogVersion(GleifRegistrationAuthorityCatalog.VERSION);
        properties.setRaCatalogDigest(GleifRegistrationAuthorityCatalog.DIGEST);
        properties.setDataRightsApprovalRef("TEST-APPROVAL-01");
        properties.setTenantIds(Set.of(TENANT.getValue()));
        properties.setLegalJurisdictions(Set.of("GB"));
        properties.getSharedQuota().setMaximumRequests(60);
        properties.getSharedQuota().setWindow(Duration.ofMinutes(1));
        return properties;
    }

    private static String envelope(String record) { return "{\"data\":" + record + "}"; }
    private static String envelopeArray(String record) { return "{\"data\":[" + record + "]}"; }
    private static String record() {
        return """
                {"type":"lei-records","attributes":{"lei":"00000000000000000001",
                "entity":{"category":"GENERAL","jurisdiction":"GB","status":"ACTIVE",
                "legalName":{"name":"Synthetic GLEIF Registry Ltd"},"creationDate":"2024-01-02",
                "legalAddress":{"addressLines":["1 Fixture Way"],"city":"Fixture City","country":"GB","postalCode":"FX1 1AA"},
                "registeredAt":{"id":"RA000585"},"registeredAs":"00000001",
                "person":"excluded-person-canary"},
                "registration":{"status":"ISSUED","lastUpdateDate":"2026-07-22T00:00:00Z"}}}
                """;
    }

    private static final class FixedTransport implements GleifHttpTransport {
        private final int status;
        private final byte[] body;
        private FixedTransport(int status, String body) {
            this.status = status;
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }
        @Override public Response get(URI uri, Duration timeout) { return new Response(status, body); }
    }

    private static final class RecordingQuota implements GleifSharedQuota {
        private final boolean ready;
        private final boolean grant;
        private int calls;
        private RecordingQuota(boolean ready, boolean grant) { this.ready = ready; this.grant = grant; }
        @Override public boolean ready() { return ready; }
        @Override public boolean tryAcquire(TenantId tenantId, String sourceKey, Instant now,
                                            int maximumRequests, Duration window) {
            calls++;
            return ready && grant && TENANT.sameValueAs(tenantId) && "GLEIF_LEVEL1".equals(sourceKey)
                    && maximumRequests == 60 && Duration.ofMinutes(1).equals(window);
        }
    }
}
