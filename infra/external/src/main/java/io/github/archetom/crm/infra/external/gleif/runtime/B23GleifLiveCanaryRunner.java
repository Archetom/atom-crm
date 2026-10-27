package io.github.archetom.crm.infra.external.gleif.runtime;

import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegistryScheme;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;
import io.github.archetom.crm.domain.valueobject.TenantId;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Private live canary: it obtains one public LEI only in process memory, then verifies the real
 * HTTPS adapter's bounded exact lookup and allowlist mapping. Neither the LEI nor any returned
 * registry field is printed or written to the report.
 */
public final class B23GleifLiveCanaryRunner {
    private B23GleifLiveCanaryRunner() { }

    public static void main(String[] args) {
        try {
            if (args.length != 2 || !"--allow-live-canary".equals(args[0])) {
                throw new IllegalArgumentException("explicit live-canary consent is required");
            }
            Path report = Path.of(args[1]).toAbsolutePath().normalize();
            run(report);
        } catch (Exception exception) {
            // Do not attach a native exception: its message could contain a request or source data.
            throw new IllegalStateException("GLEIF live canary failed");
        }
    }

    private static void run(Path report) throws Exception {
        GleifEnterpriseDataProperties properties = properties();
        GleifHttpTransport transport = new GleifHttpTransport.JavaHttpTransport(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build());
        String lei = bootstrapLei(transport, properties);
        GleifSharedQuota quota = new CanaryQuota();
        GleifEnterpriseDataSource source = new GleifEnterpriseDataSource(properties, quota, transport,
                new ObjectMapper(), Clock.systemUTC(), null, true);
        var observation = source.findByRegisteredIdentifier(new TenantId(1L),
                new RegisteredIdentifierKey(IdentityScope.GLOBAL_LEI_SYSTEM, RegistryScheme.LEI, lei)).orElseThrow();
        if (observation.primaryIdentifier() == null || observation.identifiers().isEmpty()
                || observation.sourceKind() == null || !observation.sourceDeclaredPartial()) {
            throw new IllegalStateException("GLEIF mapping was not safe");
        }
        writeReport(report);
    }

    private static String bootstrapLei(GleifHttpTransport transport, GleifEnterpriseDataProperties properties) throws Exception {
        GleifHttpTransport.Response response = transport.get(URI.create(
                properties.getApiBaseUri().toString()
                        + "lei-records?filter%5Bentity.category%5D=GENERAL&page%5Bsize%5D=1&page%5Bnumber%5D=1"),
                properties.getRequestTimeout());
        if (response.status() < 200 || response.status() >= 300) throw new IllegalStateException("GLEIF bootstrap unavailable");
        JsonNode data = new ObjectMapper().readTree(response.body()).path("data");
        if (!data.isArray() || data.isEmpty()) throw new IllegalStateException("GLEIF bootstrap unavailable");
        String value = data.get(0).path("attributes").path("lei").asText(null);
        if (value == null) throw new IllegalStateException("GLEIF bootstrap unavailable");
        // RegisteredIdentifierKey validates the value without emitting it.
        return new RegisteredIdentifierKey(IdentityScope.GLOBAL_LEI_SYSTEM, RegistryScheme.LEI, value).getCanonicalValue();
    }

    private static GleifEnterpriseDataProperties properties() {
        GleifEnterpriseDataProperties properties = new GleifEnterpriseDataProperties();
        properties.setEnabled(true);
        properties.setApiBaseUri(URI.create("https://api.gleif.org/api/v1/"));
        properties.setRequestTimeout(Duration.ofSeconds(15));
        properties.setMaxCandidates(1);
        properties.setMaximumObservationTtl(Duration.ofMinutes(1));
        properties.setPolicyVersion(GleifSourcePolicyCatalog.VERSION);
        properties.setPolicyDigest(GleifSourcePolicyCatalog.DIGEST);
        properties.setRaCatalogVersion(GleifRegistrationAuthorityCatalog.VERSION);
        properties.setRaCatalogDigest(GleifRegistrationAuthorityCatalog.DIGEST);
        properties.setDataRightsApprovalRef("CANARY-ONLY-APPROVAL");
        properties.setTenantIds(Set.of(1L));
        // Exact lookup is jurisdiction-independent; this policy entry is still required to prove
        // that name search would be bounded by an explicit region allowlist.
        properties.setLegalJurisdictions(Set.of("GB"));
        properties.getSharedQuota().setMaximumRequests(3);
        properties.getSharedQuota().setWindow(Duration.ofMinutes(1));
        return properties;
    }

    private static void writeReport(Path report) throws Exception {
        Path parent = report.getParent();
        if (parent == null || !Files.isDirectory(parent)) throw new IllegalArgumentException("report parent is unavailable");
        Instant now = Instant.now();
        String json = "{\"contractVersion\":\"B23_GLEIF_LIVE_CANARY_V1\","
                + "\"evidenceLevel\":\"LIVE_CANARY_PASSED\","
                + "\"adapterBuildDigest\":\"CANARY_LIVE_ONLY\","
                + "\"sourceKind\":\"OPEN_REFERENCE_INDEX\","
                + "\"policyDigest\":\"" + GleifSourcePolicyCatalog.DIGEST + "\","
                + "\"raCatalogDigest\":\"" + GleifRegistrationAuthorityCatalog.DIGEST + "\","
                + "\"quotaMode\":\"CANARY_LOCAL_ONLY\","
                + "\"passedScenarios\":[\"O2-12\"],"
                + "\"checks\":[\"HTTPS_BOUNDARY\",\"EXACT_LOOKUP\",\"ALLOWLIST_MAPPING\",\"SAFE_REPORT\"],"
                + "\"executedAt\":\"" + now + "\",\"expiresAt\":\"" + now.plus(Duration.ofMinutes(5)) + "\"}";
        Path temporary = Files.createTempFile(parent, ".b23-gleif-", ".json");
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(temporary, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            try { Files.move(temporary, report, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, report, StandardCopyOption.REPLACE_EXISTING); }
            Files.setPosixFilePermissions(report, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } finally { Files.deleteIfExists(temporary); }
    }

    private static final class CanaryQuota implements GleifSharedQuota {
        @Override public boolean ready() { return true; }
        @Override public boolean tryAcquire(TenantId tenantId, String sourceKey, Instant now,
                                            int maximumRequests, Duration window) {
            return tenantId != null && "GLEIF_LEVEL1".equals(sourceKey) && maximumRequests == 3
                    && Duration.ofMinutes(1).equals(window);
        }
    }
}
